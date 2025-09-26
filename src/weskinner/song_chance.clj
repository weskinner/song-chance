(ns weskinner.song-chance
  (:require [clj-http.client :as http]
            [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [cheshire.core :as json]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [hiccup.core :as hiccup]
            [hiccup.page :as page])
  (:gen-class))

(def default-config
  {:phish-net {:api-key "replaceme"}
   :database {:host "localhost"
              :port 5432
              :dbname "phish_songs"
              :user "postgres"
              :password "password"}})

(defn load-config
  "Load configuration from config.edn file, falling back to defaults"
  []
  (if-let [config-file (io/file "config.edn")]
    (if (.exists config-file)
      (try
        (merge default-config (edn/read-string (slurp config-file)))
        (catch Exception e
          (println "Warning: Could not parse config.edn, using defaults:" (.getMessage e))
          default-config))
      default-config)
    default-config))

(defn get-config []
  (load-config))

(defn get-db-spec
  "Get database connection spec from config"
  []
  (let [config (get-config)
        db-config (:database config)]
    {:dbtype "postgresql"
     :host (:host db-config)
     :port (:port db-config)
     :dbname (:dbname db-config)
     :user (:user db-config)
     :password (:password db-config)}))

(defn create-songs-table!
  "Create the songs table if it doesn't exist"
  []
  (let [db-spec (get-db-spec)
        create-sql "CREATE TABLE IF NOT EXISTS songs (
                      id SERIAL PRIMARY KEY,
                      song_id INTEGER UNIQUE,
                      song_name TEXT NOT NULL,
                      artist TEXT,
                      slug TEXT,
                      times_played INTEGER,
                      debut DATE,
                      last_played DATE,
                      gap INTEGER,
                      debut_permalink TEXT,
                      last_permalink TEXT,
                      abbr TEXT,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )"]
    (try
      (with-open [conn (jdbc/get-connection db-spec)]
        (jdbc/execute! conn [create-sql])
        (println "Songs table created successfully"))
      (catch Exception e
        (println "Error creating table:" (.getMessage e))))))

(defn insert-song!
  "Insert a single song into the database"
  [db-spec song]
  (try
    (sql/insert! db-spec :songs
                 {:song_id (:songid song)
                  :song_name (:song song)
                  :artist (:artist song)
                  :slug (:slug song)
                  :times_played (:times_played song)
                  :debut (when (:debut song) (java.sql.Date/valueOf (:debut song)))
                  :last_played (when (:last_played song) (java.sql.Date/valueOf (:last_played song)))
                  :gap (:gap song)
                  :debut_permalink (:debut_permalink song)
                  :last_permalink (:last_permalink song)
                  :abbr (:abbr song)})
    (catch Exception e
      (println "Error inserting song" (:song song) ":" (.getMessage e)))))

(defn load-songs-to-db!
  "Load songs from JSON file to database"
  [json-file-path]
  (try
    (let [json-data (json/parse-string (slurp json-file-path) true)
          songs (get-in json-data [:data])
          db-spec (get-db-spec)]
      
      (println "Creating songs table...")
      (create-songs-table!)
      
      (println (str "Loading " (count songs) " songs to database..."))
      (doseq [song songs]
        (insert-song! db-spec song))
      
      (println "Songs loaded successfully!"))
    (catch Exception e
      (println "Error loading songs to database:" (.getMessage e)))))

(defn load-latest-songs-to-db!
  "Load the most recent phish-songs JSON file to database"
  []
  (let [data-dir (io/file "data")
        json-files (filter #(and (.isFile %)
                                 (str/starts-with? (.getName %) "phish-songs-")
                                 (str/ends-with? (.getName %) ".json"))
                          (.listFiles data-dir))]
    (if (empty? json-files)
      (println "No phish-songs JSON files found in data directory")
      (let [latest-file (->> json-files
                            (sort-by #(.lastModified %))
                            last)]
        (println "Loading from:" (.getName latest-file))
        (load-songs-to-db! (.getPath latest-file))))))

(defn query-songs
  "Query songs from database with optional filters"
  [& {:keys [limit artist min-plays] :or {limit 10}}]
  (try
    (let [db-spec (get-db-spec)
          base-query "SELECT song_name, artist, times_played, debut, last_played FROM songs"
          where-clauses (cond-> []
                          artist (conj "artist ILIKE ?")
                          min-plays (conj "times_played >= ?"))
          where-clause (when (seq where-clauses)
                        (str " WHERE " (str/join " AND " where-clauses)))
          order-clause " ORDER BY times_played DESC, song_name"
          limit-clause (str " LIMIT " limit)
          full-query (str base-query where-clause order-clause limit-clause)
          params (cond-> []
                   artist (conj (str "%" artist "%"))
                   min-plays (conj min-plays))]
      
      (with-open [conn (jdbc/get-connection db-spec)]
        (jdbc/execute! conn (into [full-query] params))))
    (catch Exception e
      (println "Error querying songs:" (.getMessage e))
      [])))

(defn show-top-songs
  "Show top played songs"
  [& [limit]]
  (let [songs (query-songs :limit (or limit 20))]
    (println "\n=== Top Played Songs ===")
    (doseq [song songs]
      (println (format "%-40s | %-20s | %3d plays | Debut: %s"
                      (:songs/song_name song)
                      (or (:songs/artist song) "Phish")
                      (:songs/times_played song)
                      (:songs/debut song))))))

(defn get-recent-songs
  "Get songs played since a given date"
  [since-date]
  (try
    (let [db-spec (get-db-spec)
          query "SELECT song_name, artist, times_played, debut, last_played, gap, last_permalink 
                 FROM songs 
                 WHERE last_played >= ?::date
                 ORDER BY last_played DESC, times_played DESC"
          params [since-date]]
      
      (with-open [conn (jdbc/get-connection db-spec)]
        (jdbc/execute! conn (into [query] params))))
    (catch Exception e
      (println "Error querying recent songs:" (.getMessage e))
      [])))

(defn generate-html-page
  "Generate HTML page for recent songs"
  [songs since-date]
  (let [title (str "Phish Songs Played Since " since-date)
        song-count (count songs)
        ordered-songs (sort-by :song songs)]
    (page/html5
      {:lang "en"}
      [:head
       [:meta {:charset "UTF-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
       [:title title]
       [:style "
         body { 
           font-family: 'Georgia', serif; 
           margin: 0; 
           padding: 20px; 
           background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
           min-height: 100vh;
         }
         .container { 
           max-width: 1200px; 
           margin: 0 auto; 
           background: white; 
           border-radius: 10px; 
           padding: 30px;
           box-shadow: 0 10px 30px rgba(0,0,0,0.2);
         }
         h1 { 
           color: #333; 
           text-align: center; 
           border-bottom: 3px solid #667eea;
           padding-bottom: 10px;
           margin-bottom: 30px;
         }
         .stats {
           text-align: center;
           background: #f8f9fa;
           padding: 15px;
           border-radius: 5px;
           margin-bottom: 30px;
           color: #666;
         }
         .song-grid { 
           display: grid; 
           grid-template-columns: repeat(auto-fill, minmax(350px, 1fr)); 
           gap: 20px; 
         }
         .song-card {
           border: 1px solid #ddd;
           border-radius: 8px;
           padding: 20px;
           background: #fafafa;
           transition: all 0.3s ease;
         }
         .song-card:hover {
           transform: translateY(-2px);
           box-shadow: 0 5px 15px rgba(0,0,0,0.1);
           background: #f0f8ff;
         }
         .song-title { 
           font-size: 1.2em; 
           font-weight: bold; 
           color: #333; 
           margin-bottom: 8px;
         }
         .song-artist { 
           color: #666; 
           font-style: italic; 
           margin-bottom: 10px;
         }
         .song-stats {
           display: grid;
           grid-template-columns: 1fr 1fr;
           gap: 10px;
           font-size: 0.9em;
         }
         .stat-item {
           background: white;
           padding: 8px;
           border-radius: 4px;
           border-left: 3px solid #667eea;
         }
         .stat-label { 
           font-weight: bold; 
           color: #555; 
         }
         .stat-value { 
           color: #333; 
         }
         .permalink {
           margin-top: 15px;
         }
         .permalink a {
           color: #667eea;
           text-decoration: none;
           font-size: 0.9em;
           border: 1px solid #667eea;
           padding: 5px 10px;
           border-radius: 4px;
           display: inline-block;
           transition: all 0.3s ease;
         }
         .permalink a:hover {
           background: #667eea;
           color: white;
         }
         .footer {
           margin-top: 40px;
           text-align: center;
           color: #666;
           font-size: 0.9em;
           border-top: 1px solid #ddd;
           padding-top: 20px;
         }
       "]]
      [:body
       [:div.container
        [:h1 title]
        [:div.stats
         [:p (str "Found " song-count " songs played since " since-date)]
         [:p (str "Generated on " (java.time.LocalDate/now))]]
        [:div.song-grid
         (for [song ordered-songs]
           [:div.song-card
            [:div.song-title (:songs/song_name song)]
            [:div.song-artist 
             (str "by " (or (:songs/artist song) "Phish"))]
            [:div.song-stats
             [:div.stat-item
              [:div.stat-label "Times Played:"]
              [:div.stat-value (:songs/times_played song)]]
             [:div.stat-item
              [:div.stat-label "Last Played:"]
              [:div.stat-value (:songs/last_played song)]]
             [:div.stat-item
              [:div.stat-label "Debut:"]
              [:div.stat-value (or (:songs/debut song) "Unknown")]]
             [:div.stat-item
              [:div.stat-label "Gap (days):"]
              [:div.stat-value (or (:songs/gap song) "N/A")]]]
            (when (:songs/last_permalink song)
              [:div.permalink
               [:a {:href (:songs/last_permalink song)
                    :target "_blank"}
                "View Last Performance"]])])]
        [:div.footer
         [:p "Data from Phish.net API | Generated by song-chance"]
         [:p "Click on performance links to view show details on Phish.net"]]]])))

(defn pretty-print-html
  "Pretty print HTML with proper indentation"
  [html-string]
  (let [lines (-> html-string
                 (str/replace #"><" ">\n<")
                 (str/split #"\n"))
        indent-level (atom 0)]
    (->> lines
         (map str/trim)
         (filter #(not (str/blank? %)))
         (map (fn [line]
                (cond
                  ;; Closing tag - decrease indent first
                  (re-find #"^</" line)
                  (do (swap! indent-level #(max 0 (dec %)))
                      (str (apply str (repeat (* 2 @indent-level) " ")) line))
                  
                  ;; Self-closing tag or content without tags
                  (or (re-find #"/>" line) 
                      (not (re-find #"^<" line))
                      (and (re-find #"^<" line) (re-find #"</" line)))
                  (str (apply str (repeat (* 2 @indent-level) " ")) line)
                  
                  ;; Opening tag - indent then increase level
                  :else
                  (let [indented (str (apply str (repeat (* 2 @indent-level) " ")) line)]
                    (swap! indent-level inc)
                    indented))))
         (str/join "\n"))))

(defn generate-static-site!
  "Generate static HTML site for songs played since given date"
  [& {:keys [since-date output-dir] 
      :or {since-date "2025-01-01" 
           output-dir "site"}}]
  (try
    (println (str "Generating static site for songs since " since-date "..."))
    
    ;; Create output directory
    (io/make-parents (str output-dir "/index.html"))
    
    ;; Get recent songs
    (let [songs (get-recent-songs since-date)]
      (if (empty? songs)
        (println (str "No songs found since " since-date))
        ;; Generate HTML  
        (let [html-content (generate-html-page songs since-date)
              pretty-html (pretty-print-html html-content)]
          (spit (str output-dir "/index.html") pretty-html)
          (println (str "Generated site with " (count songs) " songs"))
          (println (str "Site saved to: " output-dir "/index.html"))
          (println (str "Open file://" (.getAbsolutePath (io/file output-dir "index.html")) " in your browser")))))
    
    (catch Exception e
      (println "Error generating static site:" (.getMessage e)))))

(defn run-shell-command
  "Helper function to run shell commands"
  [command show-output]
  (try
    (let [result (shell/sh "bash" "-c" command)]
      (when show-output
        (println (:out result))
        (when-not (str/blank? (:err result))
          (println "Error:" (:err result))))
      (:exit result))
    (catch Exception _
      (println "Error running command:" command)
      1)))

(defn init-git-repo!
  "Initialize git repository if not exists"
  []
  (let [git-dir (io/file ".git")]
    (when-not (.exists git-dir)
      (println "Initializing git repository...")
      (run-shell-command "git init" false))))

(defn setup-github-pages!
  "Setup repository for GitHub Pages deployment"
  []
  (println "🚀 Setting up GitHub Pages deployment...")
  
  ;; Create .gitignore for GitHub Pages
  (let [pages-gitignore "# Dependencies\nnode_modules/\n.cpcache/\n.calva/\n\n# Config (contains API keys)\nconfig.edn\n\n# Data files\ndata/\n\n# Build artifacts\ntarget/\n\n# IDE\n.vscode/\n.idea/"]
    (spit ".gitignore" pages-gitignore)
    (println "✅ Created .gitignore"))
  
  ;; Create README for GitHub Pages
  (let [pages-readme "# Phish Songs Database\n\n🎸 **Live Site**: [View on GitHub Pages](https://your-username.github.io/song-chance/)\n\nA dynamic database of Phish songs with performance statistics, featuring:\n\n- **Real-time data** from Phish.net API\n- **Beautiful responsive design** with song cards\n- **Performance statistics** and show links\n- **Automatic updates** via GitHub Actions\n- **Mobile-friendly** interface\n\n## Features\n\n- 🎵 Complete song database with performance counts\n- 📊 Last played dates and debut information\n- 🔗 Direct links to Phish.net show pages\n- 📱 Responsive grid layout\n- ⚡ Fast static site generation\n\n## Development\n\n```bash\n# Generate site locally\nclojure -M:run-m generate-site\n\n# Deploy to GitHub Pages\ngit push origin main\n```\n\nPowered by [Clojure](https://clojure.org/) and [Phish.net API](https://api.phish.net/)"]
    (spit "README.md" pages-readme)
    (println "✅ Created GitHub Pages README"))
  
  ;; Instructions for user
  (println "\n📋 Next steps:")
  (println "1. Create a new repository on GitHub")
  (println "2. Add your Phish.net API key as a secret:")
  (println "   - Go to Settings > Secrets and Variables > Actions")
  (println "   - Add secret: PHISH_NET_API_KEY = your-api-key")
  (println "3. Enable GitHub Pages:")
  (println "   - Go to Settings > Pages")
  (println "   - Source: GitHub Actions")
  (println "4. Push your code:")
  (println "   git add .")
  (println "   git commit -m \"Initial commit with GitHub Pages\"")
  (println "   git branch -M main")
  (println "   git remote add origin https://github.com/username/repo.git")
  (println "   git push -u origin main")
  (println "\n🌟 Your site will be available at: https://username.github.io/repo-name/"))

(defn deploy-to-github!
  "Deploy current site to GitHub Pages"
  []
  (try
    (println "🚀 Deploying to GitHub Pages...")
    
    ;; Generate fresh site
    (generate-static-site!)
    
    ;; Check if git repo exists
    (if (.exists (io/file ".git"))
      (do
        ;; Add and commit changes
        (run-shell-command "git add ." false)
        (run-shell-command "git commit -m \"Update site with latest data\"" false)
        (run-shell-command "git push origin main" false)
        (println "✅ Pushed to GitHub - deployment will start automatically"))
      
      (do
        (println "❌ No git repository found")
        (println "Run 'clojure -M:run-m setup-pages' first")))
    
    (catch Exception _
      (println "Error deploying to GitHub"))))

(defn show-help []
  (println "song-chance - Phish song data fetcher and database loader")
  (println "\nUsage:")
  (println "  clojure -M:run-m demo              # Fetch data from APIs")
  (println "  clojure -M:run-m create-table      # Create songs table")
  (println "  clojure -M:run-m load-songs        # Load latest JSON to database")
  (println "  clojure -M:run-m load-file <path>  # Load specific JSON file")
  (println "  clojure -M:run-m top-songs [limit] # Show top played songs")
  (println "  clojure -M:run-m query             # Query database")
  (println "  clojure -M:run-m generate-site     # Generate HTML site for 2025 songs")
  (println "  clojure -M:run-m generate-site-date YYYY-MM-DD # Generate site for custom date")
  (println "  clojure -M:run-m setup-pages       # Setup GitHub Pages deployment")
  (println "  clojure -M:run-m deploy            # Deploy to GitHub Pages")
  (println "  clojure -M:run-m help              # Show this help"))

(defn save-json-file
  "Save data as a JSON file with timestamp"
  [data filename-prefix]
  (let [timestamp (-> (java.time.LocalDateTime/now)
                     .toString
                     (str/replace ":" "-")
                     (str/replace "T" "_"))
        filename (str "data/" filename-prefix "-" timestamp ".json")]
    (io/make-parents filename)
    (spit filename (json/generate-string data {:pretty true}))
    (println (str "Saved JSON data to: " filename))
    filename))

(defn fetch-and-save
  "Generic function to fetch from a URL and save response as JSON"
  [url filename-prefix & [options]]
  (try
    (let [response (http/get url (merge {:as :json} options))
          json-data (:body response)]
      (save-json-file json-data filename-prefix)
      response)
    (catch Exception e
      (println (str "Error fetching from " url ": " (.getMessage e)))
      nil)))

(defn fetch-random-user
  "Fetch a random user from JSONPlaceholder API and save to JSON"
  []
  (fetch-and-save "https://jsonplaceholder.typicode.com/users/1" "jsonplaceholder-user"))

(defn fetch-posts
  "Fetch all posts from JSONPlaceholder API and save to JSON"
  []
  (fetch-and-save "https://jsonplaceholder.typicode.com/posts" "jsonplaceholder-posts"))

(defn fetch-shows
  "Fetch all shows from phish.net api and save to JSON file"
  []
  (let [config (get-config)
        api-key (get-in config [:phish-net :api-key])]
    (if api-key
      (try
        (let [response (http/get (str "https://api.phish.net/v5/songs.json?apikey=" api-key)
                                {:as :json})
              json-data (:body response)]
          ;; Save to JSON file
          (save-json-file json-data "phish-songs")
          response)
        (catch Exception e
          (println "Error fetching shows:" (.getMessage e))
          nil))
      (do
        (println "Error: No Phish.net API key configured")
        nil))))

(defn demo-api-calls
  "Demonstrate fetching JSON from public APIs and saving to files"
  []
  (println "=== Fetching a single user ===")
  (when-let [response (fetch-random-user)]
    (pp/pprint (:body response)))
  
  (println "\n=== Fetching first 3 posts ===")
  (when-let [response (fetch-posts)]
    (pp/pprint (take 3 (:body response))))

  (println "\n=== Fetching Phish.net songs ===")
  (when-let [response (fetch-shows)]
    (let [songs (get-in response [:body :response :data])]
      (if songs
        (pp/pprint (take 3 songs))
        (println "No songs data found")))))

(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (case (first args)
    "demo" (demo-api-calls)
    "create-table" (create-songs-table!)
    "load-songs" (load-latest-songs-to-db!)
    "load-file" (if (second args)
                   (load-songs-to-db! (second args))
                   (println "Usage: clojure -M:run-m load-file <json-file-path>"))
    "top-songs" (show-top-songs (when (second args) (Integer/parseInt (second args))))
    "query" (let [results (query-songs :limit 10)]
              (pp/pprint results))
    "generate-site" (generate-static-site!)
    "generate-site-date" (if (second args)
                          (generate-static-site! :since-date (second args))
                          (println "Usage: clojure -M:run-m generate-site-date YYYY-MM-DD"))
    "setup-pages" (setup-github-pages!)
    "deploy" (deploy-to-github!)
    "help" (show-help)
    (greet {:name (first args)})))