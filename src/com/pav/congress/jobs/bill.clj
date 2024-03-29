(ns com.pav.congress.jobs.bill
  (:require [aws.sdk.s3 :as s3]
            [cheshire.core :as ch]
            [clojure.tools.logging :as log]
            [com.pav.congress.bill.bill :refer [persist-bills persist-billmetadata apply-bill-id
                                                format-pav-tags]]
            [clojure.core.async :refer [timeout chan alts!! >!! go-loop]]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(defn- drained?
  "Check if map has :drained key."
  [map]
  (contains? map :drained))

(defn batch-and-persist [connections channel batch-size promise opts]
  (go-loop []
    (let [timeout-chan (timeout 5000)
          batch (->> (range batch-size)
                     (map (fn [_]
                            (let [result (first (alts!! [channel timeout-chan] :priority true))]
                              result)))
                     (remove (comp nil?)))]
      (if (-> batch last drained?)
        (do (persist-bills connections (filter #(not (contains? % :drained)) batch) opts)
            (deliver promise true))
        (do
          (when-not (empty? batch)
            (persist-bills connections batch opts))
          (recur))))))

(defn- filter-json-keys
  "Make sure :key element in map ends with 'data.json'."
  [object-map]
  (let [key (:key object-map)]
    (if (re-find #"/data.json$" key)
      key)))

(defn gather-all-keys-for [cred keys bucket prefix marker truncated? delimiter]
  (log/info "Gathering Bill Keys from s3 folder " prefix)
  (if truncated?
    (let [{objects :objects
           next-marker :next-marker
           prefix :prefix
           truncated? :truncated?
           delimiter :delimiter}
          (s3/list-objects cred bucket {:prefix prefix
                                        :marker marker
                                        :truncated? truncated?
                                        :delimiter delimiter})]
      (gather-all-keys-for cred (into keys (mapv filter-json-keys objects)) bucket prefix next-marker truncated? delimiter))
    (do (log/info "Finished gathering Bill keys from s3")
        (conj keys :finished))))

(defn sync-bills [connections cred s3-info opts]
  (log/info "Started Syncing Bills")
  (let [promise (promise)
        channel (chan 1024)
        _ (batch-and-persist connections channel 100 promise opts)
        keys (->> (gather-all-keys-for cred [] (:legislator-bucket s3-info) (:bills-prefix s3-info) nil true nil)
                  ;; TODO: move (complement nil?) to own name (e.g. 'not-nil?' fn)
                  (filterv (complement nil?)))]
    (doseq [key keys]
      (log/info (str "Reading Bill " key))
      (if (= key :finished)
        (>!! channel {:drained true})
        (>!! channel (ch/parse-string (slurp (:content (s3/get-object cred (:legislator-bucket s3-info) key))) true))))
    @promise
    (log/info "Finished Syncing Bills")))

(defn- read-s3-file
  "Read File from bucket with prefix"
  [bucket prefix cred]
  (:content (s3/get-object cred bucket prefix)))

(defn sync-billmetadata [es-connection cred bucket prefix]
  (let [file-contents (csv/read-csv (slurp (read-s3-file bucket prefix cred)))
        keys (map keyword (first file-contents))
        metadata (->> (map #(zipmap keys %) (rest file-contents))
                      (map #(apply-bill-id %))
                      (map format-pav-tags)
                      (map #(dissoc % :comment_infavor :comment_against)))]
    (log/info "Persisting " (count metadata) " Bill Metadata Entries")
    (persist-billmetadata es-connection cred metadata)))

(defn sync-fake-bills [es-connection file-loc]
  (let [fbs (ch/parse-string (slurp file-loc) true)]
    (persist-bills [es-connection] fbs {:reindex true})))
