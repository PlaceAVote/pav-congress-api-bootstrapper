(defproject pav-congress-api-bootstrapper "0.1.6-SNAPSHOT"
  :description "PlaceAVote bills parser and indexer"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [environ "1.0.0"]
                 [clj-yaml "0.4.0"]
                 [clojurewerkz/elastisch "2.2.1"]
                 [clj-aws-s3 "0.3.10" :exclusions [joda-time]] ;; prevent version range mismatch
                 [joda-time "2.8.2"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.0.2"]
                 [org.apache.logging.log4j/log4j-core "2.0.2"]
                 [cheshire "5.5.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [clojurewerkz/quartzite "2.0.0"]
                 [com.taoensso/carmine "2.12.0" :exclusions [org.clojure/tools.reader]]
                 [clojure-msgpack "1.1.2"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/data.csv "0.1.3"]
                 [com.taoensso/encore "2.33.0"]
                 [clj-http "2.1.0"]]
  :plugins [[lein-environ "1.0.0"]
						[lein-release "1.0.5"]]
  :lein-release {:scm :git
                 :deploy-via :lein-install
                 :build-uberjar true}
  :target-path "target/%s"
  :main com.pav.congress.main
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :resource-paths ["resources"]
  :profiles {:jvm-opts ^:replace ["-Xms256m" "-Xmx512m" "-Xss512k" "-XX:MaxMetaspaceSize=150m"]
             :uberjar {:aot :all
                       :uberjar-name "pav-congress-api-bootstrapper.jar"}
             :dev {
                   :dependencies [[midje "1.7.0"]]
                   :plugins [[lein-midje "3.1.3"]]
                   :env {:access-key "REPLACE_ME"
                         :secret-key "REPLACE_ME"
                         :sync-script "./bin/data-sync.sh"
                         :legislator-bucket "congress-bulk-data"
                         :legislator-prefix "congress-legislators/legislators-current.yaml"
                         :socialmedia-prefix "congress-legislators/legislators-social-media.yaml"
                         :committees-prefix "congress-legislators/committees-current.yaml"
                         :committee-members "congress-legislators/committee-membership-current.yaml"
                         :bills-prefix "congress/114/bills/"
                         :photo-bucket-url "https://s3-us-west-2.amazonaws.com/congress-bulk-data/photos"
                         :es-url "http://localhost:9200"
                         :redis-url "redis://127.0.0.1:6379"
                         :bill-metadata-bucket "congress-metadata"
                         :bill-metadata-prefix "bills/bill-metadata.csv"}}})
