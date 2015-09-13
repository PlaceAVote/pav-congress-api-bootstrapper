(ns com.pav.congress.main
  (:gen-class)
  (:require [com.pav.congress.jobs.legislator :refer [sync-legislators]]
            [environ.core :refer [env]]
            [clojurewerkz.elastisch.rest :refer [connect]]))

(defn -main []
  (let [es-connection (connect (:es-url env))]
    (sync-legislators es-connection  (:cred env) (:legislator env))))
