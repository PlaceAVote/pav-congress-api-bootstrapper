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
        (assoc :jurisdiction_source (:jurisdiction_source committee))
        (assoc :subcommittees (:subcommittees committee)))))

(defn cleanse-committees [committees]
  (->> committees
       (map cleanse-committee)))

(defn add-members [committee committee-members]
  (let [committee-key (keyword (get-in committee [:thomas_id]))
        members (get-in committee-members [committee-key])]
    (assoc committee :members members)))

(defn merge-committees-with-members [committees committee-members]
  (map (fn [committee]
         (add-members committee committee-members)) committees))

(defn persist-committees [connection committees committee-members]
  (let [cleansed-committees (cleanse-committees committees)
        committees-with-ids (apply-committee-ids cleansed-committees)
        merged-with-members (merge-committees-with-members committees-with-ids committee-members)
        bulk-payload (esrb/bulk-index merged-with-members)]
    (esrb/bulk-with-index-and-type connection "congress" "committee" bulk-payload)))
