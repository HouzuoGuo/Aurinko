(set! *warn-on-reflection* true)
(ns Aurinko.slfun
  (:require (Aurinko [skiplist :as sl]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(defn -main [& args]
  (Thread/sleep 10000)
  (let [list (sl/new "skiplist" 8 2 compare)]
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
    
    (sl/x list 20 (fn [_] true))
    (sl/x list 50 (fn [_] true))
    
;    (doseq [i (range 14)]
;      (prn (sl/node-at list i)))
    
    (prn (sl/lookup list 10 (fn [_] true)))
    (prn (sl/lookup list 20 (fn [_] true)))
    (prn (sl/lookup list 30 (fn [_] true)))
    (prn (sl/lookup list 40 (fn [_] true)))
    (prn (sl/lookup list 50 (fn [_] true)))
    
    (time (doseq [i (range 10000)]
            (sl/insert list (rand-int 10000))))
    (time (doseq [i (range 10000)]
            (sl/lookup list (rand-int 10000) (fn [_] true))))
    ))