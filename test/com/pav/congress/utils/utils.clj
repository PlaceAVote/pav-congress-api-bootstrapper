(ns com.pav.congress.utils.utils
  (:require [clojurewerkz.elastisch.rest :refer [connect]]
            [clojurewerkz.elastisch.rest.index :refer [create delete]]
            [clj-yaml.core :refer [parse-string]]
            [clojure.tools.logging :as log]
            [cheshire.core :as ch]))

(def connection (connect))

(def congress-mapping (parse-string (slurp "resources/mappings/mappings.json") true))
(def legislators (parse-string (slurp "test-resources/congress-legislators/legislators-current.yaml") true))
(def legislators-social-media (parse-string (slurp "test-resources/congress-legislators/legislators-social-media.yaml") true))
(def committees (parse-string (slurp "test-resources/congress-legislators/committees-current.yaml") true))
(def committee-members (parse-string (slurp "test-resources/congress-legislators/committee-membership-current.yaml") true))

(def bills [(ch/parse-string (slurp "test-resources/bills/hr/hr2/data.json") true)
            (ch/parse-string (slurp "test-resources/bills/hr/hr1764/data.json") true)
            (ch/parse-string (slurp "test-resources/bills/hr/hr4127/data.json") true)
            (ch/parse-string (slurp "test-resources/bills/hr/hr2669/data.json") true)
						(ch/parse-string (slurp "test-resources/bills/s/s32/data.json") true)])

(defn clean-congress-index []
  (log/info "Dropping congress index...")
  (delete connection "congress")
  (log/info "Loading congress mapping...")
  (println congress-mapping)
  (create connection "congress" :mappings congress-mapping))
