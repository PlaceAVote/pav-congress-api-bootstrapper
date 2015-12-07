(ns com.pav.congress.bill.bill
  (:require [clojurewerkz.elastisch.rest.bulk :as esrb]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest :as r]
            [clojurewerkz.elastisch.rest.document :as erd]
            [clojurewerkz.elastisch.rest.response :as ersp]
            [taoensso.carmine :as car :refer (wcar)]
            [msgpack.core :as msg]
            msgpack.clojure-extensions
            [clojure.tools.logging :as log]
            [clojure.data :as d]
            [clojure.pprint :refer [pprint]]))

;; FIXME: to utils file
(defn- pprint-str
  "Pretty print to string."
  [obj]
  (with-out-str
    (pprint obj)))

(defn- apply-id
  "Add _id key to have the same value as bill_id. This is necessary as some ES actions
through elastisch library requires :_id."
  [bill]
  (assoc bill :_id (get-in bill [:bill_id])))

(defn- next-bill-state
  "Take current bill state and returns vector of next logical states, based
on https://docs.google.com/document/d/140YX14km0-tQlJA7qEACM1K0p7b4o58Fd8PTghSoqhw/edit
document. Returns nil if is invalid or reached the end."
  [state]
  (condp = state
    "INTRODUCED"       "REFERRED"
    "REFERRED"         "REPORTED"
    "PASS_OVER:HOUSE"  ["FAIL:ORIGINATING_HOUSE" "PASSED:BILL"]
    "PASSED:BILL"      "ENACTED:SIGNED"
    "PASS_OVER:SENATE" ["FAIL:SECOND:SENATE" "PASSED:BILL"]
    nil))

(defn valid-state?
  "Use previous and current states and see does they makes any sense."
  [prev next]
  (let [n (next-bill-state prev)]
    (if (vector? n)
      ;; 'some' will return 'nil' if fails, but return false instead for tests
      (boolean
       (some #(= next %) n))
      (= next n))))

(defn- bill-diff
  "Calculate differences recursively between two bills. If found, returns
a vector where the first element is map of differences found in first bill and
the second a map of differences found in second bill. If no differences found, returns nil."
  [b1 b2]
  (let [[d1 d2 _] (d/diff b1 b2)]
    (when (or d1 d2)
      [d1 d2])))

(defn- get-bill-type [type]
  (case type
    "hr"      "house-bill"
    "hres"    "house-resolution"
    "hconres" "house-concurrent-resolution"
    "hjres"   "house-joint-resolution"
    "s"       "senate-bill"
    "sres"    "senate-resolution"
    "sconres" "senate-concurrent-resolution"
    "sjres"   "senate-joint-resolution"
    nil))

(defn- get-urls
  "Return map of urls based on bill map data."
  [bill]
  (let [congress (:congress bill)
        type (:bill_type bill)
        number (:number bill)
        bill_id (:bill_id bill)]
    (if-not type
      {:opencongress (str "https://www.opencongress.org/bill/" bill_id)}
      {:congress (str "http://beta.congress.gov/bill/" congress "th/" (get-bill-type type) "/" number)
       :govtrack (str "https://www.govtrack.us/congress/bills/" congress "/" type number)
       :opencongress (str "https://www.opencongress.org/bill/" bill_id)})))

(defn prepare-bill-summary [summary]
	(if summary
		(clojure.string/replace summary #"\n" "<br />")
		"No Summary Present..."))

(defn- filter-votes-from-actions
  "Scan vector of maps and return only those with 'vote' type."
  [actions]
  (filterv #(= "vote" (:type %)) actions))

(defn- cleanse-bill
  "Get only interested bits from parsed bill body."
  [bill]
  (let [last-action (last (get-in bill [:actions]))
        vote-actions (filter-votes-from-actions (get-in bill [:actions]))
        last-vote (last vote-actions)
        sponsor-details (get-in bill [:sponsor])]
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
        (assoc :sponsor_id (get-in sponsor-details [:thomas_id]))
        (assoc :sponsor sponsor-details)
        (assoc :cosponsors_count (count (get-in bill [:cosponsors])))
        (assoc :related_bill_id (map :bill_id (get-in bill [:related_bills])))
        (assoc :last_action last-action)
        (assoc :urls (get-urls bill))
        (assoc :last_vote last-vote)
        (assoc :summary (prepare-bill-summary (get-in bill [:summary :text])))
        (assoc :keywords (get-in bill [:subjects]))
        (assoc :votes vote-actions))))

(defn- find-bill
  "Lookup for bill details in ES and (in case of multiple results), return only
first one. Returns nil if not found."
  [connection id]
  (let [results (erd/search connection "congress" "bill" :query (q/term :bill_id id))
        n       (ersp/total-hits results)]
    (when-not (= n 0)
      (when (> n 1)
        ;; FIXME: in case of multiple results, use the one with highest score
        (log/warnf "Got multiple results for id '%s' (%d). Using only the first one" id n))
      ;; elastisch will return vector of maps always, no matter if we got one or more results
      (-> results ersp/hits-from first :_source))))

(defn- prepare-bill
  "Do some preparation for indexing."
  [b]
  (cleanse-bill b))

(defn- index-bill!
  "Index bill. Before actual putting the document inside ES index,
check if the bill already exists and does have updated/changed values, firing up event on
Redis if does. Also, if document is updated, update ES index too."
  [connections b]
  {:pre [(coll? connections)]}
  (let [prepared-bill (prepare-bill b)
        id            (:bill_id b)
        [es-conn redis-conn] connections]
    (if-let [found (find-bill es-conn id)]
      (let [old-status (:status found)
            new-status (:status b)]
        ;; actual bill difference is only detected if status was changed, hence bill content
        ;; updates, unless status was changed will not be captured
        (if (= old-status new-status)
          (log/infof "Status for bill '%s' unchanged. Skipping it" id)
          (do
            ;; check if new status is the one we know about
            (let [expected (next-bill-state old-status)]
              (when-not (= expected new-status)
                (log/warnf "Unexpected new status for bill '%s'. Got '%s' but should be '%s'" id new-status expected)))
            ;; compute bill differences, for easier logging purposes
            (when-let [diff (bill-diff b found)]
              (log/infof "Bill differences:\n[new]\n%s\n----\n[old]\n%s\n"
                         (-> diff first pprint-str)
                         (-> diff second pprint-str)))
            ;; notify redis about this
            (let [msg {:id id
                       :new new-status
                       :old old-status}]
              (log/info (str "Sending to Redis: " msg))
              (try
                (->> msg
                     msg/pack
                     (car/publish "pav-congress-api-bootstrapper")
                     (wcar redis-conn))
                (catch Exception e
                  (log/error e "Failed ending message to Redis"))))
            ;; update bill
            (log/infof "Updating bill '%s'..." id)
            (erd/replace es-conn "congress" "bill" id prepared-bill))))
      (do
        ;; otherwise index new bill; here is used 'put' instead of 'create'
        (log/infof "Adding bill '%s'..." id)
        (erd/put es-conn "congress" "bill" id prepared-bill)))))

(defn persist-bills
  "Store all bills in ES instance."
  [connections bills]
  (doseq [b bills]
    (index-bill! connections b)))

;(defn- cleanse-bills [bills]
;  (map cleanse-bill bills))
;
;(defn persist-bills
;  "Store all bills in ES instance."
;  [connection bills]
;  (let [cleansed-bills (cleanse-bills bills)
;        bills-with-ids (map apply-id cleansed-bills)
;        bulk-payload (esrb/bulk-index bills-with-ids)]
;    (log/info (str "Persisting " (count bulk-payload) " Bills to Elasticsearch"))
;    (esrb/bulk-with-index-and-type connection "congress" "bill" bulk-payload)))

(comment
  (-> (r/connect "http://127.0.0.1:9200")
      (find-bill "hconres11-114")
      :status
      clojure.pprint/pprint)

  (def SAMPLE {:votes [], :urls {:congress "http://beta.congress.gov/bill/114th/house-concurrent-resolution/11", :govtrack "https://www.govtrack.us/congress/bills/114/hconres11", :opencongress "https://www.opencongress.org/bill/hconres11-114"}, :bill_id "hconres111234-114", :last_action_at "2015-01-30", :number "11", :short_title nil, :history {:active false, :awaiting_signature false, :enacted false, :vetoed false}, :congress "114", :keywords ["blood and blood diseases" "Commemorative events and holidays" "Congressional tributes" "Health" "Organ and tissue donation and transplantation" "Social work, volunteer service, charitable organizations"], :summary "Supports the designation of National Blood Donor Month.\n\nAcknowledges the important role of volunteer blood donors in protecting the health and emergency preparedness of the United States.\n\nRecognizes the need to promote a safe, stable blood supply and to increase volunteer participation of blood donors.\n\nEndorses efforts to update blood donation policies in a safe and scientifically sound manner.\n\nRecognizes the roles of America's Blood Centers, AABB, and the American Red Cross in ensuring the safety of the blood supply and delivering lifesaving blood and blood products to health providers and patients.", :updated_at "2015-11-14T06:54:36-05:00", :bill_type "hconres", :last_vote nil, :status "REFERRED", :official_title "Expressing support for designation of January 2015 as \"National Blood Donor Month\".", :related_bill_id ["sres56-114"], :last_action {:acted_at "2015-01-30", :in_committee nil, :references [], :text "Referred to the Subcommittee on Health.", :type "referral"}, :introduced_at "2015-01-28", :sponsor_id "01967", :cosponsors_count 2, :subject "Health", :last_vote_at nil, :sponsor {:district "5", :name "Quigley, Mike", :state "IL", :thomas_id "01967", :title "Rep", :type "person"}})

  (-> (r/connect "http://127.0.0.1:9200")
      (find-bill "hconres111234-114"))

  (pprint (prepare-bill SAMPLE))

  (-> [(r/connect "http://127.0.0.1:9200") {:spec {:uri "redis://127.0.0.1:6379"}}]
      (index-bill! SAMPLE))

  (-> (r/connect "http://127.0.0.1:9200")
      ;(erd/search "congress" "bill" :query (q/term :bill_id "hconres1-114"))
      (erd/search "congress" "bill" :query (q/term :bill_id "hconres111234-114"))
      ;(erd/search "congress" "bill" :query (q/term :summary "blood"))
      ersp/hits-from
      ;ersp/total-hits
      ;(get-in [:hits :total])
      clojure.pprint/pprint
      )

  )
