(defproject pav-congress-api-bootstrapper "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [environ "1.0.0"]
                 [clj-yaml "0.4.0"]
                 [clojurewerkz/elastisch "2.1.0"]
                 [clj-aws-s3 "0.3.10"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.0.2"]
                 [org.apache.logging.log4j/log4j-core "2.0.2"]
                 [cheshire "5.5.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [clojurewerkz/quartzite "2.0.0"]]
  :plugins [[lein-environ "1.0.0"]]
  :target-path "target/%s"
  :main com.pav.congress.main
  :min-lein-version "2.0.0"
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :resource-paths ["resources"]
  :profiles {:uberjar {:aot :all
                       :uberjar-name "pav-congress-api-bootstrapper.jar"}
             :dev {
                   :dependencies [[midje "1.7.0"]]
                   :plugins [[lein-midje "3.1.3"]]
                   :env {:access-key "REPLACE_HERE"
                         :secret-key "REPLACE_HERE"
                         :legislator-bucket "congress-bulk-data"
                         :legislator-prefix "congress-legislators/legislators-current.yaml"
                         :socialmedia-prefix "congress-legislators/legislators-social-media.yaml"
                         :committees-prefix "congress-legislators/committees-current.yaml"
                         :committee-members "congress-legislators/committee-membership-current.yaml"
                         :bills-prefix "congress/114/bills/"
                         :photo-bucket-url "https://s3-us-west-2.amazonaws.com/congress-bulk-data/photos"
                         :es-url "http://localhost:9200"}}})
