(ns Aurinko.slfun
  (:require (Aurinko [skiplist :as sl]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(defn -main [& args]
  (let [list (sl/new "skiplist" 2 2 compare)]
    (sl/insert list 50)
    (sl/insert list 40)
    (sl/insert list 80)
    (sl/insert list 90)
    (sl/insert list 10)
    (sl/insert list 60)
    (sl/insert list 30)
    (sl/insert list 20)
    (sl/insert list 70)
    ))