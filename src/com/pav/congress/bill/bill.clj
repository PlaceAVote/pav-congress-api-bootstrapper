(ns com.pav.congress.bill.bill
  (:require [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest :as r]
            [clojurewerkz.elastisch.rest.document :as erd]
            [clojurewerkz.elastisch.rest.index :as eri]
            [clojurewerkz.elastisch.rest.response :as ersp]
            [clojurewerkz.elastisch.rest.bulk :as erb]
            [clojurewerkz.elastisch.common.bulk :as ecb]
            [taoensso.carmine :as car :refer (wcar)]
            [msgpack.core :as msg]
            [environ.core :refer [env]]
            msgpack.clojure-extensions
            [clojure.tools.logging :as log]
            [clojure.data :as d]
            [clojure.string :refer [lower-case]]
            [com.pav.congress.utils :refer [pprint-str]]
            [clj-http.client :as http]
            [aws.sdk.s3 :as s3]
            [image-resizer.format :as format]
            [image-resizer.resize :refer :all]
            [image-resizer.scale-methods :refer :all]))

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

(defn retrieve-subject
	"For subjects that match onboarding subjects, mark them with those subject names"
	[subject]
	(case (lower-case subject)
		"arts, culture, religion" "religion"
		"government operations and politics" "politics"
		"crime and law enforcement" "drugs, gun rights"
		"armed forces and national security" "defense"
		"science, technology, communications" "technology"
		"economics and public finance" "economics"
		"social welfare" "social issues"
		subject))

(defn apply-bill-id
  "Assoc bill_id with :_id field"
  [bill]
  (assoc bill :_id (str (:bill_id bill) "-" (:congress bill))
              :bill_id (str (:bill_id bill) "-" (:congress bill))))

(defn party-affiliation-count
  "Unfortunately we can't determine there party from the bill payload so we need to make a call to the elasticsearch
  index for legislators to obtain this count."
  [cosponsors es-conn]
  (if (empty? cosponsors)
    {:republican 0 :democrat 0 :independent 0}
    (let [sponsors-to-lookup (map #(assoc {} :_id (:thomas_id %)) cosponsors)
          parties (->> (erd/multi-get es-conn "congress" "legislator" sponsors-to-lookup)
                    (map #(get-in % [:_source :current_term :party])))]
      {:republican  (count (filter #(= "Republican" %) parties))
       :democrat    (count (filter #(= "Democrat" %) parties))
       :independent (count (filter #(= "Independent" %) parties))})))

(defn- cleanse-bill
  "Get only interested bits from parsed bill body."
  [bill es-conn]
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
        (assoc :cosponsors_count (party-affiliation-count (get-in bill [:cosponsors]) es-conn))
        (assoc :related_bill_id (map :bill_id (get-in bill [:related_bills])))
        (assoc :last_action last-action)
        (assoc :urls (get-urls bill))
        (assoc :last_vote last-vote)
        (assoc :summary (prepare-bill-summary (get-in bill [:summary :text])))
        (assoc :keywords (get-in bill [:subjects]))
        (assoc :votes vote-actions))))

(defn format-pav-tags [meta]
  (update-in meta [:pav_tags] #(->> (clojure.string/split % #",")
                                    (map clojure.string/trim))))

(defn- find-bill
  "Lookup for bill details in ES and (in case of multiple results), return only
first one. Returns nil if not found."
  [connection id]
  (let [results (erd/search connection "congress" "bill" :query (q/term :_id id))
        n       (ersp/total-hits results)]
    (when-not (= n 0)
      (when (> n 1)
        ;; FIXME: in case of multiple results, use the one with highest score
        (log/warnf "Got multiple results for id '%s' (%d). Using only the first one" id n))
      ;; elastisch will return vector of maps always, no matter if we got one or more results
      (-> results ersp/hits-from first :_source))))

(defn- prepare-bill
  "Do some preparation for indexing."
  [b es-conn]
  (cleanse-bill b es-conn))

(def image-resize-fn
  (fn [width height]
    (resize-fn width height ultra-quality)))

(defn- upload-featured-image
  "Upload featured image to s3 bucket"
  [creds bucket key img-width img-height link]
  (let [{stream :body headers :headers} (http/get link {:insecure? true :as :stream})
        content-type (headers "Content-Type")
        outgoing-stream (format/as-stream ((image-resize-fn img-width img-height) stream) "jpg")]
    (log/info "Uploading main image for " key " of type " content-type)
    (s3/put-object creds bucket key outgoing-stream {:content-type content-type})))

(defn- put-bill-metadata [connection {:keys [_id] :as m}]
  (log/info "Indexing bill metadata for " _id)
  (erd/put connection "congress" "billmeta" _id (dissoc m :_id)))

(defn- update-bill-metadata [connection {:keys [_id] :as m}]
  (log/info "Updating bill metadata for " _id)
  (erd/update-with-partial-doc connection "congress" "billmeta" _id (dissoc m :_id)))

(defn- index-billmetadata
  "Index bill metadata under type billmeta"
  [connection s3-creds bill-metadata]
  (doseq [{:keys [_id congress bill_id featured_img_link] :as m} bill-metadata]
    (let [site-key (str "bills/" congress "/images/" bill_id "/main.jpg")
          fb-key (str "bills/" congress "/images/" bill_id "/fb.jpg")
          twitter-key (str "bills/" congress "/images/" bill_id "/twitter.jpg")
          img_url (str "https://cdn.placeavote.com/" site-key)
          fb_url (str "https://cdn.placeavote.com/" fb-key)
          twitter_url (str "https://cdn.placeavote.com/" twitter-key)
          m-to-persist (assoc m :featured_img_link img_url
                                :featured_img_links {:site_url img_url :facebook_url fb_url :twitter_url twitter_url})]
      (upload-featured-image s3-creds "placeavote-cdn" site-key 1000 1000 featured_img_link)
      (upload-featured-image s3-creds "placeavote-cdn" fb-key 900 473 featured_img_link)
      (upload-featured-image s3-creds "placeavote-cdn" twitter-key 280 150 featured_img_link)
      (if (erd/get connection "congress" "billmeta" _id)
        (update-bill-metadata connection m-to-persist)
        (put-bill-metadata connection m-to-persist))))
  (eri/refresh connection "congress"))

(defn- index-bill!
  "Index bill. Before actual putting the document inside ES index,
check if the bill already exists and does have updated/changed values, firing up event on
Redis if does. Also, if document is updated, update ES index too."
  [connections b]
  {:pre [(coll? connections)]}
  (let [[es-conn redis-conn] connections
        prepared-bill (prepare-bill b es-conn)
        id            (:bill_id b)]
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
            (when (:notify-on-bill-changed? env)
              (let [msg {:id id
                         :new new-status
                         :old old-status}]
                (log/info (str "Sending to Redis: " msg))
                (try
                  (wcar redis-conn
                        (->> msg
                             msg/pack
                             (car/publish "pav-congress-api-bootstrapper")))
                  (catch Exception e
                    (log/error e "Sending bill status message to Redis failed")))))
            ;; update bill
            (log/infof "Updating bill '%s'..." id)
            (erd/replace es-conn "congress" "bill" id prepared-bill))))
      (do
        ;; otherwise index new bill; here is used 'put' instead of 'create'
        (log/infof "Adding bill '%s'..." id)
        (erd/put es-conn "congress" "bill" id prepared-bill)))))

(defn index-bills
  "Index all bills"
  [connections bills {:keys [reindex]}]
  (let [[es-conn _] connections
        prepared-bills (map #(prepare-bill % es-conn) bills)]
    (if reindex
      (let [batch (-> (map apply-id prepared-bills) ecb/bulk-index)]
        (erb/bulk-with-index-and-type es-conn "congress" "bill" batch)
        (log/info (str "Reindexing " (count batch) " bills to Elasticsearch")))
      (doseq [{:keys [bill_id] :as b} prepared-bills]
        (if (erd/get es-conn "congress" "bill" bill_id)
          (do
            (log/info "Replacing bill " bill_id)
            (erd/update-with-partial-doc es-conn "congress" "bill" bill_id b))
          (do
            (log/info "Indexing bill " bill_id)
            (erd/put es-conn "congress" "bill" bill_id b)))))))

(defn persist-bills
  "Store all bills in ES instance."
  [connections bills opts]
  (index-bills connections bills opts))

(defn persist-billmetadata
  "Store all bill metadata in ES instance"
  [connection s3-creds bill-metadata]
  (index-billmetadata connection s3-creds bill-metadata))

(comment
  (-> (r/connect "http://127.0.0.1:9200")
      (find-bill "hconres11-114")
      :status
      clojure.pprint/pprint)

  (-> (r/connect "https://search-pav-elastic-ewkjqgqpzlamhgsy5b6mokc4te.us-west-2.es.amazonaws.com/")
      ;(find-bill "hconres11-114")
      (erd/search "congress" "bill" :query (q/term :_status "REFERRED"))
      clojure.pprint/pprint)

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
