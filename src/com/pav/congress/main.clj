(ns com.pav.congress.main
  (:gen-class)
  (:require [com.pav.congress.jobs.legislator :refer [sync-legislators]]
            [com.pav.congress.jobs.committee :refer [sync-committees]]
            [com.pav.congress.jobs.bill :refer [sync-bills sync-billmetadata]]
            [environ.core :refer [env]]
            [clojurewerkz.elastisch.rest :refer [connect]]
            [clojure.tools.logging :as log]
            [clojurewerkz.elastisch.rest.index :as eri]
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.jobs :refer [defjob]]
            [clojurewerkz.quartzite.jobs :as j]
            [clojurewerkz.quartzite.triggers :as t]
            [clojurewerkz.quartzite.schedule.daily-interval :refer [schedule
                                                                    with-interval-in-hours]]
            [clojure.java.io :as io]
            [clojure.tools.cli :as cli])
  (:import java.lang.Runtime
           [java.io InputStreamReader BufferedReader]))

(defmacro stream-looper
  "Simple looping construct to read content from stream and output it
to 'logger' facility. It is macro mainly to handle macro-like arguments like 'log/info'."
  [stream logger]
  (let [line (gensym)]
    `(loop []
       (when-let [~line (.readLine ~stream)]
         (~logger ~line)
         (recur)))))

(defn- run-sh-script
  "This is similar to clojure.java.shell/sh, except it will do output using
logging functions. Returns only exit-code."
  [cmd]
  (let [proc      (. (Runtime/getRuntime) exec cmd)
        as-reader #(-> % InputStreamReader. BufferedReader.)]
    (with-open [stdout (-> proc .getInputStream as-reader)
                stderr (-> proc .getErrorStream as-reader)]
      (let [out (future (stream-looper stdout log/info))
            err (future (stream-looper stderr log/error))
            ret (.waitFor proc)]
        ;; force futures to process any pending operations so we can get output
        @out @err
        ret))))

(defn- start-rsync-script
  "Start local script for fetching files."
  []
  (run-sh-script (:sync-script env)))

(defn- start-sync-job
  "Start sync job, by connecting to S3 with set credentials, parsing them and
filling elasticsearch instance."
  [& opts]
  (let [es-url (:es-url env)
        es-connection (connect es-url)
        redis-url {:spec {:uri (:redis-url env)} :pool {}}
        es-redis-connection [es-connection :redis redis-url]
        creds (select-keys env [:access-key :secret-key])]
    (log/infof "Connecting to ElasticSearch at %s..." es-url)
    ;(eri/update-mapping es-connection "congress" "bill" (slurp "resources/mappings/mappings.json"))

    ;; Read specific files from S3 and fill es. This could be refactored, but the code
    ;; is short anyways.
    (->> [:legislator-bucket :legislator-prefix :socialmedia-prefix]
         (select-keys env)
         (sync-legislators es-connection creds))

    (->> [:legislator-bucket :committees-prefix :committee-members]
         (select-keys env)
         (sync-committees es-connection creds))

    (sync-bills es-redis-connection creds (select-keys env [:legislator-bucket :bills-prefix]) (first opts))

    (log/info "Refreshing Congress Index")
    (eri/refresh es-connection "congress")))

(defn- start-billmetadata-sync-job
  "Sync bill meta data from file or s3 resource"
  [bucket prefix]
  (let [es-url (:es-url env)
        es-connection (connect es-url)
        creds (select-keys env [:access-key :secret-key])]
    (log/infof "Connecting to ElasticSearch at %s..." es-url)
    (sync-billmetadata es-connection creds bucket prefix)))

(defn- run-all
  "Run everything."
  [& opts]
  (try
    ;(start-rsync-script)
    (start-sync-job (first opts))
    (catch Exception e
      ;; log error so we can know if something happened with es or s3 connection
      (log/error e "Sync job failed:"))))

(defjob SyncJob [ctx]
  (run-all))

(defn init-quartz-job []
  (let [s   (-> (qs/initialize) qs/start)
        job  (j/build
               (j/of-type SyncJob)
               (j/with-identity (j/key "jobs.noop.1")))
        now-trigger (t/build
                      (t/with-identity (t/key "triggers.1"))
                      (t/with-schedule (schedule
                                         (with-interval-in-hours 12))))]
    (log/info "Waiting for job to run")
    (qs/schedule s job now-trigger)
    (log/info "Finished job")))

(def cli-opts
  [["-h" "--help" "Print Help"]
   [nil "--run-job" "Run Bootstrapper Job" :default false :flag true]
   [nil "--schedule-job" "Schedule Bootstrapper Job" :default false :flag true]
   [nil "--sync-billmetadata" "Sync Bill Metadata from File" :default false :flag true]
   [nil "--reindex-bills" "Bulk index all bills" :default false :flag true]])

(defn usage [options-summary]
  (->> ["Congress Bootstrapper Usage."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  --run-job             Run Bootstrapper Job"
        "  --schedule-job        Schedule Bootstrapper Job"
        "  --sync-billmetadata   Sync bill meta data from file"
        "  --reindex-bills       Bulk index all bills"
        ""
        "Please refer to the manual page for more information."]
    (clojure.string/join \newline)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn error [status msg]
  (log/error msg)
  (System/exit status))

(defn -main
  "Main application entry point."
  [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-opts)]
    (when errors
      (error 1 errors))
    (when (:help options)
      (exit 0 (usage summary)))
    (when (true? (:run-job options))
      (log/info "Running Bootstrapper Job Now")
      (run-all {:reindex (:reindex-bills options)}))
    (when (true? (:schedule-job options))
      (log/info "Scheduling Bootstrapper Job Now")
      (init-quartz-job))
    (when (:sync-billmetadata options)
      (start-billmetadata-sync-job (:bill-metadata-bucket env) (:bill-metadata-prefix env)))))
