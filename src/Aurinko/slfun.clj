(ns Aurinko.slfun
  (:require (Aurinko [skiplist :as sl]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(defn -main [& args]
  (let [list (sl/new "skiplist" 3 2 compare)]
    (sl/store list 10)
    (sl/store list 20)
    (sl/store list 30)
    (sl/store list 40)
    (sl/store list 50)
    (sl/store list 60)
    (sl/store list 70)
    (sl/store list 80)
    (prn (sl/get list 10))
    (prn (sl/get list 20))
    (prn (sl/get list 30))
    (prn (sl/get list 40))
    (prn (sl/get list 50))
    (prn (sl/get list 60))
    (prn (sl/get list 70))
    (prn (sl/get list 80))
    (prn (sl/get list 90))
    (prn (sl/get list 100))
    (sl/x list 1)))