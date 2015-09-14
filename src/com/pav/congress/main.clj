(ns com.pav.congress.main
  (:gen-class)
  (:require [com.pav.congress.jobs.legislator :refer [sync-legislators]]
            [com.pav.congress.jobs.committee :refer [sync-committees]]
            [com.pav.congress.jobs.bill :refer [sync-bills]]
            [environ.core :refer [env]]
            [clojurewerkz.elastisch.rest :refer [connect]]
            [clojure.tools.logging :as log]))

(defn -main []
  (log/info (str "Environment " env))
  (let [es-connection (connect (:es-url env))
        creds (select-keys env [:access-key :secret-key])
        legislator-info (select-keys env [:legislator-bucket :legislator-prefix :socialmedia-prefix])
        committee-info (select-keys env [:legislator-bucket :committees-prefix :committee-members])
        bill-info (select-keys env [:legislator-bucket :bills-prefix])]
    (sync-legislators es-connection creds legislator-info)
    (sync-committees es-connection creds committee-info)
    (sync-bills es-connection creds bill-info)))
