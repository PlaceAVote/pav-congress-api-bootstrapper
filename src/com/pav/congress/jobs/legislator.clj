(ns com.pav.congress.jobs.legislator
  (:require [clj-yaml.core :refer [parse-string]]
            [aws.sdk.s3 :refer [get-object]]
            [com.pav.congress.legislator.legislator :refer [persist-legislators]]
            [clojure.tools.logging :as log]))

(defn sync-legislators
  "Sync legislators from S3 to es."
  [es-connection cred s3-info]
  (let [current-legislators (parse-string (slurp (:content (get-object cred (:legislator-bucket s3-info) (:legislator-prefix s3-info)))))
        social-media-info (parse-string (slurp (:content (get-object cred (:legislator-bucket s3-info) (:socialmedia-prefix s3-info)))))
        len (count current-legislators)]
    (log/infof "Persisting %d Legislators to Elasticsearch" len)
    (persist-legislators es-connection current-legislators social-media-info)
    (log/infof "Finished persisting %d Legislators to Elasticsearch" len)))
