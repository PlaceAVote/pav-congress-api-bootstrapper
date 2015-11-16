(ns com.pav.congress.jobs.legislator
  (:require [clj-yaml.core :refer [parse-string]]
            [aws.sdk.s3 :refer [get-object]]
            [com.pav.congress.legislator.legislator :refer [persist-legislators]]
            [com.pav.congress.jobs.committee :refer [parse-s3-yaml-file]]
            [clojure.tools.logging :as log]))

(defn sync-legislators
  "Sync legislators from S3 to es."
  [es-connection cred s3-info]
  (let [bucket (:legislator-bucket s3-info)
        current-legislators (-> cred
                                (get-object bucket (:legislator-prefix s3-info))
                                parse-s3-yaml-file)
        social-media-info (-> cred
                              (get-object bucket (:socialmedia-prefix s3-info))
                              parse-s3-yaml-file)
        len (count current-legislators)]
    (log/infof "Persisting %d Legislators to Elasticsearch" len)
    (persist-legislators es-connection current-legislators social-media-info)
    (log/infof "Finished persisting %d Legislators to Elasticsearch" len)))
