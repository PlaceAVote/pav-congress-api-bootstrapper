(ns com.pav.congress.bill.bill-test
  (:use midje.sweet)
  (:require [com.pav.congress.utils.utils :refer [clean-congress-index
                                                  bills
                                                  connection]]
            [com.pav.congress.bill.bill :refer [persist-bills]]
            [clojurewerkz.elastisch.rest.document :as esd]))

(against-background [(before :facts (clean-congress-index))]
 (facts "Test cases cover the creation of bills in the elasticsearch index, parsing and persisting a cleaner data
        set"
        (fact "Given a collection of bills, parse and persist to elasticsearch congress index under the type bill"
              (let [_ (persist-bills connection bills)
                    persisted-bill (:_source (esd/get connection "congress" "bill" "hr2-114"))]
                persisted-bill => {:bill_id "hr2-114"
                                   :bill_type "hr"
                                   :number "2"
                                   :congress "114"
                                   :sponsor {:district "26"
                                             :name "Burgess, Michael C."
                                             :state "TX"
                                             :thomas_id "01751"
                                             :title "Rep"
                                             :type "person"}
                                   :status "ENACTED:SIGNED"
                                   :related_bill_id ["hres173-114"
                                                     "hr284-114"
                                                     "hr289-114"
                                                     "hr380-114"
                                                     "hr663-114"
                                                     "hr804-114"
                                                     "hr1021-114"
                                                     "hr1372-114"
                                                     "hr1470-114"
                                                     "s148-114"
                                                     "s332-114"
                                                     "s810-114"]
                                   :short_title "Medicare Access and CHIP Reauthorization Act of 2015"
                                   :official_title "To amend title XVIII of the Social Security Act to repeal the Medicare sustainable growth rate and strengthen Medicare access by improving physician payments and making other improvements, to reauthorize the Children's Health Insurance Program, and for other purposes."
                                   :introduced_at "2015-03-24"
                                   :history {:active true
                                             :active_at "2015-03-25T19:05:00-04:00"
                                             :awaiting_signature false
                                             :enacted true
                                             :enacted_at "2015-04-16"
                                             :house_passage_result "pass"
                                             :house_passage_result_at "2015-03-26T12:08:00-04:00"
                                             :senate_passage_result "pass"
                                             :senate_passage_result_at "2015-04-14"
                                             :vetoed false}
                                   :last_action {:acted_at "2015-04-16"
                                                 :congress "114"
                                                 :law "public"
                                                 :number "10"
                                                 :references []
                                                 :text "Became Public Law No: 114-10."
                                                 :type "enacted"}
                                   :last_vote {:acted_at "2015-04-14"
                                                :how "roll"
                                                :references []
                                                :result "pass"
                                                :roll "144"
                                                :status "PASSED:BILL"
                                                :text "Passed Senate without amendment by Yea-Nay Vote. 92 - 8. Record Vote Number: 144."
                                                :type "vote"
                                                :vote_type "vote2"
                                                :where "s"}
                                   :urls {:congress "http://beta.congress.gov/bill/114th/house-bill/2"
                                          :govtrack "https://www.govtrack.us/congress/bills/114/hr2"
                                          :opencongress "https://www.opencongress.org/bill/hr2-114"}
                                   :cosponsors_count 13
                                   :subject "Health"
                                   :summary (get-in (first bills) [:summary :text])}))))
