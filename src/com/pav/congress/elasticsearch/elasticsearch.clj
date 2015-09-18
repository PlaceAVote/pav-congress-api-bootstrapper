(ns com.pav.congress.elasticsearch.elasticsearch
  (:require [clojurewerkz.elastisch.rest.index :as eri]))

(defn update-es-mappings [connection mapping]
  (eri/update-mapping connection "congress" "bill" mapping))