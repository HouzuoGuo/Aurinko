(set! *warn-on-reflection* true)
(ns Aurinko.slfun
  (:require (Aurinko [skiplist :as sl]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(defn -main [& args]
  (let [list (sl/new "skiplist" 4 2 compare)]
    (sl/insert list 30)
    (sl/insert list 40)
    (sl/insert list 30)
    (sl/insert list 40)
    (sl/insert list 50)
    (sl/insert list 40)
    (sl/insert list 50)
    (sl/insert list 30)
    (sl/insert list 20)
    (sl/insert list 40)
    (sl/insert list 50)
    (sl/insert list 10)
    (sl/insert list 20)
    (sl/insert list 50)
    (sl/insert list 50)
    
    (prn (sl/lookup list 10 (fn [_] true)))
    (prn (sl/lookup list 20 (fn [_] true)))
    (prn (sl/lookup list 30 (fn [_] true)))
    (prn (sl/lookup list 40 (fn [_] true)))
    (prn (sl/lookup list 50 (fn [_] true)))
;    
;    (time (doseq [i (range 1000)]
;            (sl/insert list (rand-int 1000))))
;    (time (doseq [i (range 1000)]
;            (sl/lookup list (rand-int 1000) (fn [_] true))))
    ))