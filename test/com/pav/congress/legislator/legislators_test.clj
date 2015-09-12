(ns com.pav.congress.legislator.legislators-test
  (:use midje.sweet)
  (:require [com.pav.congress.utils.utils :refer [clean-congress-index
                                                  legislators
                                                  legislators-social-media
                                                  connection]]
            [clojurewerkz.elastisch.rest.document :as esd]
            [com.pav.congress.legislator.legislator :refer [persist-legislators]]))

(against-background [(before :facts (clean-congress-index))]
  (facts "Test cases cover processing legislator information from various yaml files, parsing and persisting them
          to the elasticsearch congress index"
         (fact "Given a collection of legislators and social media details, merge and perist details to elasticsearch"
               (let [_ (persist-legislators connection legislators legislators-social-media)
                     persisted-legislator (:_source (esd/get connection "congress" "legislator" "02222"))]
                 persisted-legislator => {:thomas "02222"
                                                    :bioguide "R000600"
                                                    :govtrack 412664
                                                    :first_name "Aumua"
                                                    :last_name "Amata"
                                                    :gender "F"
                                                    :birthday "1947-12-29"
                                                    :social {:twitter "RepAmata"
                                                             :facebook "congresswomanaumuaamata"
                                                             :facebook_id "1537155909907320"
                                                             :youtube_id "UCGdrLQbt1PYDTPsampx4t1A"}
                                                    :current_term {:type "rep"
                                                                   :start "2015-01-06"
                                                                   :end "2017-01-03"
                                                                   :state "AS"
                                                                   :district 0
                                                                   :party "Republican"
                                                                   :address "1339 Longworth HOB; Washington DC 20515-5200"
                                                                   :office "1339 Longworth House Office Building"
                                                                   :phone "202-225-8577"
                                                                   :url "https://radewagen.house.gov"
                                                                   :fax "202-225-8757"}}))))