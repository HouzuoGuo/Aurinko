(ns Aurinko.slfun
  (:require (Aurinko [skiplist :as sl]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(defn -main [& args]
  (let [list (sl/new "skiplist" 2 1)]
    (sl/kv list 2 200)
    (sl/kv list 2 201)
    (sl/kv list 2 203)
    (sl/kv list 3 300)
    (sl/kv list 1 100)
    (prn (sl/k list 1))
    (prn (sl/k list 3))
    (prn (sl/k list 2))))