(ns Aurinko.skiplist-test
  (:require (Aurinko [skiplist :as sl]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(deftest skiplist
  (let [list (sl/new "skiplist" 2 1)]
    (sl/kv list 1 100 compare)
    (sl/kv list 2 200 compare)
    (sl/kv list 3 300 compare)
    (sl/x  list 3 compare)
    (sl/kv list 4 400 compare)
    (sl/kv list 5 500 compare)
    (is (= (sl/k list 1 compare) 100))
    (is (= (sl/k list 2 compare) 200))
    (is (= (sl/k list 3 compare) 300))
    (is (= (sl/k list 4 compare) 400))
    (is (= (sl/k list 5 compare) 500))))