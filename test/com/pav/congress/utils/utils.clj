(ns com.pav.congress.utils.utils
  (:require [clojurewerkz.elastisch.rest :refer [connect]]
            [clojurewerkz.elastisch.rest.index :refer [create delete]]
            [clj-yaml.core :refer [parse-string]]))

(def connection (connect))

(def legislators (parse-string (slurp "test-resources/congress-legislators/legislators-current.yaml") true))
(def legislators-social-media (parse-string (slurp "test-resources/congress-legislators/legislators-social-media.yaml") true))

(defn clean-congress-index []
  (delete connection "congress")
  (create connection "congress"))
