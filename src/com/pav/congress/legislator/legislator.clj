(ns com.pav.congress.legislator.legislator
  (:require [clojurewerkz.elastisch.rest.document :refer [create]]
            [clojurewerkz.elastisch.rest.bulk :as esrb]))

(defn append-id [legislator]
  (assoc legislator :_id (get-in legislator [:thomas])))

(defn apply-legislator-ids [legislators]
  (map append-id legislators))

(defn cleanse-legislator [legislator]
  (let [id-data (get-in legislator [:id])
        name-data (get-in legislator [:name])
        bio-data (get-in legislator [:bio])
        current-term (last (get-in legislator [:terms]))]
    (-> {}
        (assoc :thomas (get-in id-data [:thomas]))
        (assoc :bioguide (get-in id-data [:bioguide]))
        (assoc :govtrack (get-in id-data [:govtrack]))
        (assoc :first_name (get-in name-data [:first]))
        (assoc :last_name (get-in name-data [:last]))
        (assoc :gender (get-in bio-data [:gender]))
        (assoc :birthday (get-in bio-data [:birthday]))
        (assoc :current_term current-term))))

(defn cleanse-legislators [legislators]
  (->> legislators
       (map cleanse-legislator)))

(defn persist-legislators [connection legislators social-media-details]
  (let [cleansed-legislators (cleanse-legislators legislators)
        legislators-with-ids (apply-legislator-ids cleansed-legislators)
        bulk (esrb/bulk-index legislators-with-ids)]
    (esrb/bulk-with-index-and-type connection "congress" "legislator" bulk)))
