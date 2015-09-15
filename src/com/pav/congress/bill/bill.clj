(ns com.pav.congress.bill.bill
  (:require [clojurewerkz.elastisch.rest.bulk :as esrb]
            [clojure.tools.logging :as log]))

(defn apply-id [bill]
  (assoc bill :_id (get-in bill [:bill_id])))

(defn get-bill-type [type]
  (case type
    "hr" "house-bill"
    "hres" "house-resolution"
    "hconres" "house-concurrent-resolution"
    "hjres" "house-joint-resolution"
    "s" "senate-bill"
    "sres" "senate-resolution"
    "sconres" "senate-concurrent-resolution"
    "sjres" "senate-joint-resolution"
    nil))

(defn get-urls [bill]
  (let [congress (:congress bill)
        type (:bill_type bill)
        number (:number bill)
        bill_id (:bill_id bill)]
    (if (nil? type)
      {:opencongress (str "https://www.opencongress.org/bill/" bill_id)}
      {:congress (str "http://beta.congress.gov/bill/" congress "th/" (get-bill-type type) "/" number)
       :govtrack (str "https://www.govtrack.us/congress/bills/" congress "/" type number)
       :opencongress (str "https://www.opencongress.org/bill/" bill_id)})))

(defn filter-votes-from-actions [actions]
  (filterv #(= "vote" (:type %)) actions))

(defn cleanse-bill [bill]
  (let [last-action (last (get-in bill [:actions]))
        vote-actions (filter-votes-from-actions (get-in bill [:actions]))
        last-vote (last vote-actions)]
    (-> {}
        (assoc :bill_id (get-in bill [:bill_id]))
        (assoc :bill_type (get-in bill [:bill_type]))
        (assoc :number (get-in bill [:number]))
        (assoc :congress (get-in bill [:congress]))
        (assoc :introduced_at (get-in bill [:introduced_at]))
        (assoc :last_action_at (:acted_at last-action))
        (assoc :last_vote_at (:acted_at last-vote))
        (assoc :updated_at (get-in bill [:updated_at]))
        (assoc :subject (get-in bill [:subjects_top_term]))
        (assoc :short_title (get-in bill [:short_title]))
        (assoc :official_title (get-in bill [:official_title]))
        (assoc :status (get-in bill [:status]))
        (assoc :history (get-in bill [:history]))
        (assoc :sponsor (get-in bill [:sponsor]))
        (assoc :cosponsors_count (count (get-in bill [:cosponsors])))
        (assoc :related_bill_id (map :bill_id (get-in bill [:related_bills])))
        (assoc :last_action last-action)
        (assoc :urls (get-urls bill))
        (assoc :last_vote last-vote)
        (assoc :summary (get-in bill [:summary :text]))
        (assoc :keywords (get-in bill [:subjects]))
        (assoc :votes vote-actions))))

(defn cleanse-bills [bills]
  (->> bills
       (map cleanse-bill)))

(defn persist-bills [connection bills]
  (let [cleansed-bills (cleanse-bills bills)
        bills-with-ids (map apply-id cleansed-bills)
        bulk-payload (esrb/bulk-index bills-with-ids)]
    (log/info (str "Persisting " (count bulk-payload) " Bills to Elasticsearch"))
      (esrb/bulk-with-index-and-type connection "congress" "bill" bulk-payload)))
