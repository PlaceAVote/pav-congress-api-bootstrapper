(ns com.pav.congress.bill.scaling-test
  (:use midje.sweet)
  (:require [com.pav.congress.bill.bill :as b]
            [image-resizer.resize :refer :all]
            [clojure.java.io :refer [input-stream]]))

(fact "Resize bad jpg images using TwelveMonkeys plugin"
  ;; this addresses https://github.com/PlaceAVote/pav-congress-api-bootstrapper/issues/16
  (instance? java.awt.image.BufferedImage  
             (-> (input-stream "test-resources/bad-images/image1.jpg")
                 ((b/image-resize-fn 1000 1000)))) => true
                 
  (instance? java.awt.image.BufferedImage  
             (-> (input-stream "test-resources/bad-images/image2.jpg")
                 ((b/image-resize-fn 1000 1000)))) => true)
