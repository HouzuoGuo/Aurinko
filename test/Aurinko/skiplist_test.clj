(ns Aurinko.skiplist-test
  (:require (Aurinko [skiplist :as sl]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(def ^:const TEST-SIZE 500) ; Test sample size

(deftest skiplist
  (let [list        (sl/new "skiplist" 8 3 compare)
        numbers     (range TEST-SIZE)
        repetitions (for [i (range TEST-SIZE)] (max (rand-int 10) 1))
        deleted     (for [i (range TEST-SIZE)] (rand-int 2))]
    ; Insert repeating values
    (doseq [i (shuffle (range TEST-SIZE))]
      (doseq [j (range (nth repetitions i))]
        (sl/insert list i)))
    ; Delete values
    (doseq [i (range TEST-SIZE)]
      (when (= (nth deleted i) 1)
        (sl/x list i (fn [_] true))))
    (doseq [i (range TEST-SIZE)]
      (let [result (sl/lookup list i (fn [_] true))]
        (is (= (count result) (nth repetitions i))) ; Number of values found is right
        (is (every? #(= (:v %) i) result)) ; Values are right
        (when (= (nth deleted i) 1)        ; Deleted are invalid
          (is (every? #(= (:valid %) false) result)))))))

(.delete (file "skiplist"))