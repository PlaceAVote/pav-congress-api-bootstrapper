(ns com.pav.congress.main
  (:gen-class)
  (:require [com.pav.congress.jobs.legislator :refer [sync-legislators]]
            [environ.core :refer [env]]
            [clojurewerkz.elastisch.rest :refer [connect]]
            [clojure.tools.logging :as log]))

(defn -main []
  (log/info (str "Environment " env))
  (let [es-connection (connect (:es-url env))
        creds (select-keys env [:access-key :secret-key])
        legislator-info (assoc (select-keys env [:legislator-bucket :legislator-prefix])
                          :socialmedia-prefix (:socialmedia-prefix env))]
    (sync-legislators es-connection creds legislator-info)))
