(ns Aurinko.skiplist-test
  (:require (Aurinko [skiplist :as sl]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(deftest skiplist
  (let [list (sl/new "skiplist" 8 4)]))

(.delete (file "skiplist"))