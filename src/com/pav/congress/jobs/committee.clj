(ns com.pav.congress.jobs.committee
  (:require [clj-yaml.core :refer [parse-string]]
            [aws.sdk.s3 :refer [get-object]]
            [com.pav.congress.committee.committee :refer [persist-committees]]
            [clojure.tools.logging :as log]))

(defn sync-committees
  "Synchronize comittees by reading data from S3."
  [es-connection cred s3-info]
  (let [current-committees (parse-string (slurp (:content (get-object cred (:legislator-bucket s3-info) (:committees-prefix s3-info)))))
        committee-members (parse-string (slurp (:content (get-object cred (:legislator-bucket s3-info) (:committee-members s3-info)))))
        len (count current-committees)]
    (log/infof "Persisting %d Comittees to Elasticsearch" len)
    (persist-committees es-connection current-committees committee-members)
    (log/infof "Finished persisting %d Comittees to Elasticsearch" len)))
