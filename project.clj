(defproject pav-congress-api-bootstrapper "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [environ "1.0.0"]
                 [clj-yaml "0.4.0"]
                 [clojurewerkz/elastisch "2.1.0"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {
                   :dependencies [[midje "1.7.0"]]
                   :plugins [[lein-midje "3.1.3"]]
                   }})
