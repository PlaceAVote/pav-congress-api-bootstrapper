(ns com.pav.congress.legislator.legislator
  (:require [clojurewerkz.elastisch.rest.document :refer [create]]
            [clojurewerkz.elastisch.rest.bulk :as esrb]
            [environ.core :refer [env]]
            [clojure.tools.logging :refer [spy]]))

(def ^{:doc "Local variable for storing bucket url. Public, since tests are using it."
       :const true}
  photo-bucket-url (:photo-bucket-url env))

(defn- append-id
  "Setup :_id to be the same as :thomas."
  [legislator]
  (assoc legislator :_id (get-in legislator [:thomas])))

(defn- apply-legislator-ids [legislators]
  (map append-id legislators))

(defn- construct-img-url-paths
  "Add image paths, constructed from legislators govtrack id."
  [legislator]
  (let [govtrack_id (:govtrack legislator)
				photo-bucket-url (:photo-bucket-url env)]
    (assoc legislator :img_urls {:200px (str photo-bucket-url "/" govtrack_id "-200px.jpeg")
                                 :100px (str photo-bucket-url "/" govtrack_id "-100px.jpeg")
                                 :50px (str photo-bucket-url "/" govtrack_id "-50px.jpeg")})))

(defn- cleanse-legislator
  "Returns a simplified map of legislator data with adjusted image paths."
  [legislator]
  (let [id-data (get-in legislator [:id])
        name-data (get-in legislator [:name])
        bio-data (get-in legislator [:bio])
        current-term (last (get-in legislator [:terms]))]
    (-> {}
        (assoc :thomas (get-in id-data [:thomas]))
        (assoc :bioguide (get-in id-data [:bioguide]))
        (assoc :govtrack (get-in id-data [:govtrack]))
        (construct-img-url-paths)
        (assoc :first_name (get-in name-data [:first]))
        (assoc :last_name (get-in name-data [:last]))
        (assoc :gender (get-in bio-data [:gender]))
        (assoc :birthday (get-in bio-data [:birthday]))
        (assoc :current_term current-term))))

(defn- find-legislator-by-thomas
  "Returns legislator details by matching thomas id."
  [legislators thomas]
  (first
   (filter #(= thomas (get-in % [:thomas])) legislators)))

(defn- cleanse-legislators
  "Apply cleanse-legislator on legislators map."
  [legislators]
  (map cleanse-legislator legislators))

(defn- merge-social-info
  "Merge social and legislator details based on thomas."
  [legislators social-media-details]
  (filter (complement nil?)
          (map (fn [social-details]
           (let [legislator (find-legislator-by-thomas
                              legislators
                              (get-in social-details [:id :thomas]))
                 social (get-in social-details [:social])]
             (if-not (empty? legislator)
               (assoc legislator :social social)))) social-media-details)))

(defn persist-legislators [connection legislators social-media-details]
  (let [cleansed-legislators (cleanse-legislators legislators)
        legislators-with-social (merge-social-info cleansed-legislators social-media-details)
        legislators-with-ids (apply-legislator-ids legislators-with-social)
        bulk (esrb/bulk-index legislators-with-ids)]
    (esrb/bulk-with-index-and-type connection "congress" "legislator" bulk)))

(comment
(defn persist-legislators-local [connection legislators social-media-details]
  (let [cleansed-legislators (cleanse-legislators legislators)
        legislators-with-social (merge-social-info cleansed-legislators social-media-details)
        legislators-with-ids (apply-legislator-ids legislators-with-social)
        bulk (esrb/bulk-index legislators-with-ids)
        ]
    (clojure.pprint/pprint bulk)
    (esrb/bulk-with-index-and-type connection "congress" "legislator" bulk)))


(persist-legislators2
 nil
 (com.pav.congress.jobs.committee/parse-yaml-file
  "./share/congress-legislators/legislators-current.yaml.new")
 (com.pav.congress.jobs.committee/parse-yaml-file
  "./share/congress-legislators/legislators-social-media.yaml"))
)
