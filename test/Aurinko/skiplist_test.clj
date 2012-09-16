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
        (sl/x list i)))
    (doseq [i (range TEST-SIZE)]
      (let [result (sl/lookup list i)]
        (is (= (count result) (nth repetitions i))) ; Number of values found is right
        (is (every? #(= (:v %) i) result)) ; Values are right
        (when (= (nth deleted i) 1)        ; Deleted are invalid
          (is (every? #(= (:valid %) false) result))))))
  ; Scan skip list
  (let [list (sl/new "skiplist2" 2 2 compare)]
    (doseq [i (range 10)]
      (sl/insert list i))
    (is (= (vec (for [thing (sl/scan<  list 5)]    (:v thing))) [0 1 2 3 4]))
    (is (= (vec (for [thing (sl/scan<  list -1)]   (:v thing))) []))
    (is (= (vec (for [thing (sl/scan<= list 5)]    (:v thing))) [0 1 2 3 4 5]))
    (is (= (vec (for [thing (sl/scan<= list -1)]   (:v thing))) []))
    (is (= (vec (for [thing (sl/scan>  list 5)]    (:v thing))) [6 7 8 9]))
    (is (= (vec (for [thing (sl/scan>  list 100)]  (:v thing))) []))
    (is (= (vec (for [thing (sl/scan>= list 5)]    (:v thing))) [5 6 7 8 9]))
    (is (= (vec (for [thing (sl/scan>= list 100)]  (:v thing))) []))
    (is (= (vec (for [thing (sl/scan<> list 5)]    (:v thing))) [0 1 2 3 4 6 7 8 9]))
    (is (= (vec (for [thing (sl/scan<> list 100)]  (:v thing))) [0 1 2 3 4 5 6 7 8 9]))
    (is (= (vec (for [thing (sl/scan>< list 2 6)]  (:v thing))) [2 3 4 5]))
    (is (= (vec (for [thing (sl/scan>< list 6 2)]  (:v thing))) [6]))
    (is (= (vec (for [thing (sl/scan>< list 10 2)] (:v thing))) [])))
  (.delete (file "skiplist"))
  (.delete (file "skiplist2")))