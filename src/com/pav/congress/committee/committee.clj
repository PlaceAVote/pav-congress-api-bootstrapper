(ns com.pav.congress.committee.committee
  (:require [clojurewerkz.elastisch.rest.bulk :as esrb]))

(defn append-id [committee]
  (assoc committee :_id (get-in committee [:thomas_id])))

(defn apply-committee-ids [committees]
  (map append-id committees))

(defn cleanse-committee [committee]
  (let []
    (-> {}
        (assoc :type (:type committee))
        (assoc :name (:name committee))
        (assoc :url (:url committee))
        (assoc :thomas_id (:thomas_id committee))
        (assoc :house_committee_id (:house_committee_id committee))
        (assoc :minority_url (:minority_url committee))
        (assoc :rss_url (:rss_url committee))
        (assoc :minority_rss_url (:minority_rss_url committee))
        (assoc :address (:address committee))
        (assoc :phone (:phone committee))
        (assoc :jurisdiction (:jurisdiction committee))
        (assoc :jurisdiction_source (:jurisdiction_source committee)))))

(defn cleanse-committees [committees]
  (->> committees
       (map cleanse-committee)))

(defn persist-committees [connection committees]
  (let [cleansed-committees (cleanse-committees committees)
        committees-with-ids (apply-committee-ids cleansed-committees)
        bulk-payload (esrb/bulk-index committees-with-ids)]
    (esrb/bulk-with-index-and-type connection "congress" "committee" bulk-payload)))
