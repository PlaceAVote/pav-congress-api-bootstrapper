(ns com.pav.congress.bill.bill-test
  (:use midje.sweet)
  (:require [com.pav.congress.utils.utils :refer [clean-congress-index
                                                  bills
                                                  connection]]
            [com.pav.congress.bill.bill :refer [persist-bills valid-state?]]
            [clojurewerkz.elastisch.rest.document :as esd]
            [environ.core :refer [env]]
            [clojure.data :as d]))

(against-background [(before :facts (clean-congress-index))]
  (fact "Given a collection of bills, parse and persist to elasticsearch congress index under the type bill"
    (let [;; Sleep until index cleanup completes. This is necessary, since 'persist-bills' will perform query
          ;; and ES will deny any queries until sharding is done, after cleanup. However, simple to ES during this process works.
          _ (Thread/sleep 1000)
          _ (persist-bills [connection {:spec {:uri (:redis-url env)}}]  bills)
          persisted-bill (:_source (esd/get connection "congress" "bill" "hr2-114"))]
      persisted-bill => {:bill_id          "hr2-114"
                         :bill_type        "hr"
                         :number           "2"
                         :congress         "114"
                         :introduced_at    "2015-03-24"
                         :last_action_at   "2015-04-16"
                         :last_vote_at     "2015-04-14"
                         :updated_at       "2015-09-12T06:19:12-04:00"
                         :sponsor_id       "01751"
                         :sponsor          {:district  "26"
                                            :name      "Burgess, Michael C."
                                            :state     "TX"
                                            :thomas_id "01751"
                                            :title     "Rep"
                                            :type      "person"}
                         :status           "ENACTED:SIGNED"
                         :related_bill_id  ["hres173-114"
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
                         :short_title      "Medicare Access and CHIP Reauthorization Act of 2015"
                         :official_title   "To amend title XVIII of the Social Security Act to repeal the Medicare sustainable growth rate and strengthen Medicare access by improving physician payments and making other improvements, to reauthorize the Children's Health Insurance Program, and for other purposes."
                         :history          {:active                   true
                                            :active_at                "2015-03-25T19:05:00-04:00"
                                            :awaiting_signature       false
                                            :enacted                  true
                                            :enacted_at               "2015-04-16"
                                            :house_passage_result     "pass"
                                            :house_passage_result_at  "2015-03-26T12:08:00-04:00"
                                            :senate_passage_result    "pass"
                                            :senate_passage_result_at "2015-04-14"
                                            :vetoed                   false}
                         :last_action      {:acted_at   "2015-04-16"
                                            :congress   "114"
                                            :law        "public"
                                            :number     "10"
                                            :references []
                                            :text       "Became Public Law No: 114-10."
                                            :type       "enacted"}
                         :last_vote        {:acted_at   "2015-04-14"
                                            :how        "roll"
                                            :references []
                                            :result     "pass"
                                            :roll       "144"
                                            :status     "PASSED:BILL"
                                            :text       "Passed Senate without amendment by Yea-Nay Vote. 92 - 8. Record Vote Number: 144."
                                            :type       "vote"
                                            :vote_type  "vote2"
                                            :where      "s"}
                         :urls             {:congress     "http://beta.congress.gov/bill/114th/house-bill/2"
                                            :govtrack     "https://www.govtrack.us/congress/bills/114/hr2"
                                            :opencongress "https://www.opencongress.org/bill/hr2-114"}
                         :cosponsors_count 13
                         :subject          "Health"
                         :summary          (clojure.string/replace (get-in (first bills) [:summary :text]) #"\n" "<br />")
                         :keywords         [
                                            "Administrative law and regulatory procedures"
                                            "Administrative remedies"
                                            "Advisory bodies"
                                            "Alternative treatments"
                                            "Border security and unlawful immigration"
                                            "Child health"
                                            "Civil actions and liability"
                                            "Congressional oversight"
                                            "Correctional facilities and imprisonment"
                                            "Crime prevention"
                                            "Department of Health and Human Services"
                                            "Digestive and metabolic diseases"
                                            "Education programs funding"
                                            "Elementary and secondary education"
                                            "Emergency medical services and trauma care"
                                            "Employment and training programs"
                                            "Family planning and birth control"
                                            "Forests, forestry, trees"
                                            "Fraud offenses and financial crimes"
                                            "Government information and archives"
                                            "Government studies and investigations"
                                            "Government trust funds"
                                            "Health"
                                            "Health care costs and insurance"
                                            "Health care coverage and access"
                                            "Health care quality"
                                            "Health facilities and institutions"
                                            "Health information and medical records"
                                            "Health personnel"
                                            "Health programs administration and funding"
                                            "Health promotion and preventive care"
                                            "Health technology, devices, supplies"
                                            "Home and outpatient care"
                                            "Hospital care"
                                            "Intergovernmental relations"
                                            "Land use and conservation"
                                            "Licensing and registrations"
                                            "Long-term, rehabilitative, and terminal care"
                                            "Medicaid"
                                            "Medical education"
                                            "Medical research"
                                            "Medicare"
                                            "Minority health"
                                            "Musculoskeletal and skin diseases"
                                            "Neurological disorders"
                                            "Performance measurement"
                                            "Poverty and welfare assistance"
                                            "Prescription drugs"
                                            "Public contracts and procurement"
                                            "Right of privacy"
                                            "Rural conditions and development"
                                            "Sex and reproductive health"
                                            "State and local finance"
                                            "State and local taxation"
                                            "Surgery and anesthesia"
                                            "Teenage pregnancy"
                                            "Tennessee"
                                            "User charges and fees"
                                            "Vocational and technical education"
                                            "Wildlife conservation and habitat protection"
                                            "Women's health"
                                            ]
                         :votes            [{:vote_type  "vote"
                                             :where      "h"
                                             :acted_at   "2015-03-26T12:08:00-04:00"
                                             :type       "vote"
                                             :references [{:reference "CR H2045-2070"
                                                           :type      "text"}]
                                             :status     "PASS_OVER:HOUSE"
                                             :result     "pass"
                                             :roll       "144"
                                             :suspension nil
                                             :how        "roll"
                                             :text       "On passage Passed by the Yeas and Nays: 392 - 37 (Roll no. 144)."}
                                            {:acted_at   "2015-04-14"
                                             :how        "roll"
                                             :references []
                                             :result     "pass"
                                             :roll       "144"
                                             :status     "PASSED:BILL"
                                             :text       "Passed Senate without amendment by Yea-Nay Vote. 92 - 8. Record Vote Number: 144."
                                             :type       "vote"
                                             :vote_type  "vote2"
                                             :where      "s"}]})))

(facts "Test various bill handling utility functions"
  (fact "Validate next logical bill state"
    (valid-state? "INTRODUCED" "REFERRED") => true
    (valid-state? "INTRODUCED" "PASS_OVER:HOUSE") => false
    (valid-state? "INTRODUCED" nil) => false
    (valid-state? "PASS_OVER:SENATE" "PASSED:BILL") => true
    (valid-state? "PASS_OVER:SENATE" "FAIL:SECOND:SENATE") => true
    (valid-state? "PASS_OVER:SENATE" "SOME:UNKNOWN:STATE") => false
    (valid-state? "SOME:UNKNOWN:STATE" "PASS_OVER:SENATE") => false))
