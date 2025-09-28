(ns weskinner.site
  (:require [hiccup.page :as page]
            [clojure.string :as str]
            [clojure.java.io :as io]))

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

(defn generate-html-page
  "Generate HTML page for recent songs with table view toggle"
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
           margin-bottom: 20px;
           color: #666;
         }
         
         /* View Toggle Switch */
         .view-toggle {
           display: flex;
           justify-content: center;
           align-items: center;
           margin-bottom: 30px;
           gap: 15px;
         }
         .switch {
           position: relative;
           display: inline-block;
           width: 60px;
           height: 34px;
         }
         .switch input {
           opacity: 0;
           width: 0;
           height: 0;
         }
         .slider {
           position: absolute;
           cursor: pointer;
           top: 0;
           left: 0;
           right: 0;
           bottom: 0;
           background-color: #667eea;
           transition: .4s;
           border-radius: 34px;
         }
         .slider:before {
           position: absolute;
           content: '';
           height: 26px;
           width: 26px;
           left: 4px;
           bottom: 4px;
           background-color: white;
           transition: .4s;
           border-radius: 50%;
         }
         input:checked + .slider {
           background-color: #764ba2;
         }
         input:checked + .slider:before {
           transform: translateX(26px);
         }
         .view-label {
           font-weight: bold;
           color: #333;
         }
         
         /* Grid View Styles */
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
         
         /* Table View Styles */
         .song-table {
           display: none;
           width: 100%;
           border-collapse: collapse;
           background: white;
           border-radius: 8px;
           overflow: hidden;
           box-shadow: 0 4px 6px rgba(0,0,0,0.1);
         }
         .song-table.active {
           display: table;
         }
         .song-table th {
           background: #667eea;
           color: white;
           padding: 15px 12px;
           text-align: left;
           font-weight: bold;
           border-bottom: 2px solid #5a67d8;
         }
         .song-table td {
           padding: 12px;
           border-bottom: 1px solid #e2e8f0;
           vertical-align: top;
         }
         .song-table tr:nth-child(even) {
           background: #f8f9fa;
         }
         .song-table tr:hover {
           background: #e6f3ff;
           cursor: pointer;
         }
         .song-table .song-name {
           font-weight: bold;
           color: #333;
         }
         .song-table .artist {
           color: #666;
           font-style: italic;
           font-size: 0.9em;
         }
         .song-table .times-played {
           font-weight: bold;
           color: #667eea;
           text-align: center;
         }
         .song-table .last-played {
           color: #555;
           text-align: center;
         }
         .song-table .debut {
           color: #777;
           font-size: 0.9em;
           text-align: center;
         }
         .song-table .gap {
           color: #888;
           text-align: center;
         }
         .song-table .permalink a {
           color: #667eea;
           text-decoration: none;
           padding: 4px 8px;
           border: 1px solid #667eea;
           border-radius: 4px;
           font-size: 0.8em;
           transition: all 0.3s ease;
         }
         .song-table .permalink a:hover {
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
         
         /* Hide grid view when table is active */
         .song-grid.hidden {
           display: none;
         }
       "]]
      [:body
       [:div.container
        [:h1 title]
        [:div.stats
         [:p (str "Found " song-count " songs played since " since-date)]
         [:p (str "Generated on " (java.time.LocalDate/now))]]
        
        ;; View Toggle Switch
        [:div.view-toggle
         [:span.view-label "Grid View"]
         [:label.switch
          [:input {:type "checkbox" :id "viewToggle" :onclick "toggleView()"}]
          [:span.slider]]
         [:span.view-label "Table View"]]
        
        ;; Grid View (default)
        [:div.song-grid {:id "gridView"}
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
        
        ;; Table View (hidden by default)
        [:table#tableView.song-table
         [:thead
          [:tr
           [:th "Song"]
           [:th "Times Played"]
           [:th "Last Played"]
           [:th "Debut"]
           [:th "Gap (days)"]
           [:th "Last Show"]]]
         [:tbody
          (for [song ordered-songs]
            [:tr
             [:td.song-name
              [:div (:songs/song_name song)]
              [:div.artist (str "by " (or (:songs/artist song) "Phish"))]]
             [:td.times-played (:songs/times_played song)]
             [:td.last-played (:songs/last_played song)]
             [:td.debut (or (:songs/debut song) "Unknown")]
             [:td.gap (or (:songs/gap song) "N/A")]
             [:td.permalink 
              (when (:songs/last_permalink song)
                [:a {:href (:songs/last_permalink song)
                     :target "_blank"}
                 "View Show"])]])]
        
        [:div.footer
         [:p "Data from Phish.net API | Generated by song-chance"]
         [:p "Click on performance links to view show details on Phish.net"]]
        
        ;; JavaScript for view toggle
        [:script "
          function toggleView() {
            const toggle = document.getElementById('viewToggle');
            const gridView = document.getElementById('gridView');
            const tableView = document.getElementById('tableView');
            
            if (toggle.checked) {
              // Show table view
              gridView.classList.add('hidden');
              tableView.classList.add('active');
            } else {
              // Show grid view
              gridView.classList.remove('hidden');
              tableView.classList.remove('active');
            }
          }"]
        ]]])))
        

(defn generate-site!
  "Generate static HTML site for songs played since given date"
  [songs since-date output-dir]
  (if (empty? songs)
    (println (str "No songs found since " since-date))
    ;; Generate HTML  
    (let [html-content (generate-html-page songs since-date)
          pretty-html (pretty-print-html html-content)]
      (io/make-parents (str output-dir "/index.html"))
      (spit (str output-dir "/index.html") pretty-html)
      (println (str "Generated site with " (count songs) " songs"))
      (println (str "Site saved to: " output-dir "/index.html"))
      (println (str "Open file://" (.getAbsolutePath (io/file output-dir "index.html")) " in your browser")))))
