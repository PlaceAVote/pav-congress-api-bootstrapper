(ns com.pav.congress.committee.committee-test
  (:use midje.sweet)
  (:require [com.pav.congress.utils.utils :refer [clean-congress-index
                                                  committees
                                                  committee-members
                                                  connection]]
            [com.pav.congress.committee.committee :refer [persist-committees]]
            [clojurewerkz.elastisch.rest.document :as esd]))

(against-background [(before :facts (clean-congress-index))]
 (facts "Test cases cover processing committee information from yaml files, parsing and persisting them to the
        elasticsearch congress index"
        (fact "Given a collection of commitees, parse and persist to elasticsearch congress index under type committee"
              (let [_ (persist-committees connection committees committee-members)
                    persisted-committee (:_source (esd/get connection "congress" "committee" "HSII"))]
                persisted-committee => {:type "house"
                                        :name "House Committee on Natural Resources"
                                        :url "http://naturalresources.house.gov/"
                                        :minority_url "http://democrats.naturalresources.house.gov/"
                                        :thomas_id "HSII"
                                        :house_committee_id "II"
                                        :address "1324 LHOB; Washington, DC 20515-6201"
                                        :phone "(202) 225-2761"
                                        :rss_url "http://naturalresources.house.gov/news.xml"
                                        :minority_rss_url "http://democrats.naturalresources.house.gov/rss.xml"
                                        :jurisdiction "The House Committee on Natural Resources considers legislation about American energy production, mineral lands and mining, fisheries and wildlife, public lands, oceans, Native Americans, irrigation and reclamation."
                                        :jurisdiction_source "http://naturalresources.house.gov/about/"
                                        :members (get-in committee-members [:HSII])
                                        :subcommittees [{:name "Energy and Mineral Resources", :thomas_id "06", :address "1333 LHOB; Washington, DC 20515", :phone "(202) 225-9297"}
                                                       {:name "Federal Lands", :thomas_id "10", :address "1332 LHOB; Washington, DC 20515", :phone "(202) 226-7736"}
                                                       {:name "Water, Power and Oceans", :thomas_id "13", :address "1522 LHOB; Washington, DC 20515", :phone "(202) 225-8331"}
                                                       {:name "Fisheries, Wildlife, Oceans and Insular Affairs", :thomas_id "22", :address "140 CHOB; Washington, DC 20515", :phone "(202) 226-0200"}
                                                       {:name "Indian, Insular and Alaska Native Affairs", :thomas_id "24", :address "4450 OFOB; Washington, DC 20515", :phone "(202) 226-9725"}
                                                       {:name "Oversight and Investigations", :thomas_id "15", :address "4170 OFOB; Washington, DC 20515", :phone "(202) 225-7107"}]}))))
