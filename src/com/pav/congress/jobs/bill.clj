(ns com.pav.congress.jobs.bill
  (:require [aws.sdk.s3 :as s3]
            [cheshire.core :as ch]
            [clojure.tools.logging :as log]
            [com.pav.congress.bill.bill :refer [persist-bills]]
            [clojure.core.async :refer [timeout chan alts!! >!! go-loop]]))

(defn- drained?
  "Check if map has :drained key."
  [map]
  (contains? map :drained))

(defn batch-and-persist [connection channel batch-size promise]
  (go-loop []
    (let [timeout-chan (timeout 5000)
          batch (->> (range batch-size)
                     (map (fn [_]
                            (let [result (first (alts!! [channel timeout-chan] :priority true))]
                              result)))
                     (remove (comp nil?)))]
      (if (-> batch last drained?)
        (do (persist-bills connection (filter #(not (contains? % :drained)) batch))
            (deliver promise true))
        (do
          (when-not (empty? batch)
            (persist-bills connection batch))
          (recur))))))

(defn- filter-json-keys
  "Make sure :key element in map ends with 'data.json'."
  [object-map]
  (let [key (:key object-map)]
    (if (re-find #"/data.json$" key)
      key)))

(defn gather-all-keys-for [cred keys bucket prefix marker truncated? delimiter]
  (log/info "Gathering Bill Keys from s3")
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

(defn sync-bills [es-connection cred s3-info]
  (log/info "Started Syncing Bills")
  (let [promise (promise)
        channel (chan 1024)
        _ (batch-and-persist es-connection channel 100 promise)
        keys (->> (gather-all-keys-for cred [] (:legislator-bucket s3-info) (:bills-prefix s3-info) nil true nil)
                  (filterv (complement nil?)))]
    (doseq [key keys]
      (log/info (str "Reading Bill " key))
      (if (= key :finished)
        (>!! channel {:drained true})
        (>!! channel (ch/parse-string (slurp (:content (s3/get-object cred (:legislator-bucket s3-info) key))) true))))
    @promise
    (log/info "Finished Syncing Bills")))
