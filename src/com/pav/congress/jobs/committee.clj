(ns com.pav.congress.jobs.committee
  (:require [clj-yaml.core :refer [parse-string]]
            [aws.sdk.s3 :refer [get-object]]
            [com.pav.congress.committee.committee :refer [persist-committees]]
            [clojure.tools.logging :as log]))

(defn parse-s3-yaml-file
  "Parse yaml file found on S3."
  [object]
  ;; TODO: this should go to utils
  (-> object :content slurp parse-string))

(defn parse-yaml-file
  "Parse yaml file found on fs."
  ;; TODO: this should go to utils
  [object]
  (-> object slurp parse-string))

(defn sync-committees
  "Synchronize comittees by reading data from S3."
  [es-connection cred s3-info]
  (let [bucket (:legislator-bucket s3-info)
        current-committees (-> cred
                               (get-object bucket (:committees-prefix s3-info))
                               parse-s3-yaml-file)
        committee-members (-> cred
                              (get-object bucket (:committee-members s3-info))
                              parse-s3-yaml-file)
        len (count current-committees)]
    (log/infof "Persisting %d Comittees to Elasticsearch" len)
    (persist-committees es-connection current-committees committee-members)
    (log/infof "Finished persisting %d Comittees to Elasticsearch" len)))
