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
    
    (prn (sl/lookup list 10))
    (prn (sl/lookup list 20))
    (prn (sl/lookup list 30))
    (prn (sl/lookup list 40))
    (prn (sl/lookup list 50))
))
    ;(time (doseq [i (range 1000)]
     ;       (sl/insert list (rand-int 100000))))))