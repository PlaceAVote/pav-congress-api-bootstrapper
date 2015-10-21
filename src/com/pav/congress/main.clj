(ns com.pav.congress.main
  (:gen-class)
  (:require [com.pav.congress.jobs.legislator :refer [sync-legislators]]
            [com.pav.congress.jobs.committee :refer [sync-committees]]
            [com.pav.congress.jobs.bill :refer [sync-bills]]
            [com.pav.congress.elasticsearch.elasticsearch :refer [update-es-mappings]]
            [environ.core :refer [env]]
            [clojurewerkz.elastisch.rest :refer [connect]]
            [clojure.tools.logging :as log]
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.jobs :refer [defjob]]
            [clojurewerkz.quartzite.jobs :as j]
            [clojurewerkz.quartzite.triggers :as t]
            [clojurewerkz.quartzite.schedule.daily-interval :refer [schedule
                                                                    with-interval-in-hours]]))

(defn start-sync-job []
  (log/info (str "Environment " env))
  (let [es-connection (connect (:es-url env))
        creds (select-keys env [:access-key :secret-key])
        legislator-info (select-keys env [:legislator-bucket :legislator-prefix :socialmedia-prefix])
        committee-info (select-keys env [:legislator-bucket :committees-prefix :committee-members])
        bill-info (select-keys env [:legislator-bucket :bills-prefix])]
    (update-es-mappings es-connection (slurp "resources/mappings/mappings.json"))
    (sync-legislators es-connection creds legislator-info)
    (sync-committees es-connection creds committee-info)
    (sync-bills es-connection creds bill-info)))

(defjob SyncJob [ctx]
  (start-sync-job))

(defn -main
  [& m]
  (let [s   (-> (qs/initialize) qs/start)
        job  (j/build
               (j/of-type SyncJob)
               (j/with-identity (j/key "jobs.noop.1")))
        now-trigger (t/build
                      (t/with-identity (t/key "triggers.1"))
                      ;(t/start-now)
                      (t/with-schedule (schedule
                                         (with-interval-in-hours 5)))
                      )]
    (log/info "Waiting for Job to Run")
    (qs/schedule s job now-trigger)
    (log/info "Finished Job")))