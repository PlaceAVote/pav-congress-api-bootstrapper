(ns com.pav.congress.jobs.legislator
  (:require [clj-yaml.core :refer [parse-string]]
            [aws.sdk.s3 :refer [get-object]]
            [com.pav.congress.legislator.legislator :refer [persist-legislators]]
            [clojure.tools.logging :as log]))

(defn sync-legislators [es-connection cred s3-info]
  (let [current-legislators (parse-string (slurp (:content (get-object cred (:bucket s3-info) (:legislator-prefix s3-info)))))
        social-media-info (parse-string (slurp (:content (get-object cred (:bucket s3-info) (:socialmedia-prefix s3-info)))))]
    (log/info (str "Persisting " (count current-legislators) " Legislators to Elasticsearch"))
    (persist-legislators es-connection current-legislators social-media-info)
    (log/info (str "Finished Persisting " (count current-legislators) " Legislators to Elasticsearch"))))
