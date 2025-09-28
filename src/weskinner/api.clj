(ns weskinner.api
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

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
  [config]
  (let [api-key (get-in config [:phish-net :api-key])]
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
  [config]
  (println "=== Fetching a single user ===")
  (when-let [response (fetch-random-user)]
    (pp/pprint (:body response)))
  
  (println "\n=== Fetching first 3 posts ===")
  (when-let [response (fetch-posts)]
    (pp/pprint (take 3 (:body response))))

  (println "\n=== Fetching Phish.net songs ===")
  (when-let [response (fetch-shows config)]
    (let [songs (get-in response [:body :response :data])]
      (if songs
        (pp/pprint (take 3 songs))
        (println "No songs data found")))))