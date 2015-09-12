(ns com.pav.congress.committee.committee-test
  (:use midje.sweet)
  (:require [com.pav.congress.utils.utils :refer [clean-congress-index
                                                  legislators
                                                  legislators-social-media
                                                  connection]]
            [clojurewerkz.elastisch.rest.document :as esd]))

(against-background [(before :facts (clean-congress-index))]
 (facts "Test cases cover processing committee information from yaml files, parsing and persisting them to the
        elasticsearch congress index"
        ))
