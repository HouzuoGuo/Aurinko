(ns Aurinko.db-test
  (:require (Aurinko [db :as db] [col :as col] [fs :as fs]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(deftest db
  (.mkdir (file "db"))
  (let [db (db/open "db")]
    ; create collections
    (db/create db "c1")
    (db/create db "c2")
    ; use collections
    (let [c1 (db/col db "c1")
          c2 (db/col db "c2")]
      (col/index-path c1 [:a])
      (col/index-path c2 [:a])
      (col/insert c1 {:a 1})
      (col/insert c1 {:b 2})
      (col/insert c2 {:c 3})
      (is (= (dissoc (first (col/all c1)) :_pos) {:a 1}))
      (is (= (dissoc (first (col/all c2)) :_pos) {:c 3}))
      ; compress collection
      (col/delete c1 (first (col/all c1)))
      (db/compress db "c1")
      (is (= (.length (file "db/c1/data")) (+ 8 (* 15 2))))
      (is (= (set (db/all db)) #{"c1" "c2"}))
      (is (= (set (col/indexed (db/col db "c1"))) #{[:a]}))
      ; repair collection
      (spit "db/c2/data" "#@*&$!(*@)") ; completely corrupt data
      (spit "db/c2/log" "#@*&$!(*@)" :append true) ; corrupt log file too
      (spit "db/c2/data" "#@*&$!(*@)" :append true)
      (spit "db/c2/log" "waefdf!(*@)" :append true)
      (spit "db/c2/data" "#fgbtsr!(*@)" :append true)
      (spit "db/c2/log" "!@adf@*&$!(*@)" :append true)
      (spit "db/c2/data" "`ds&$!(*@)" :append true)
      (spit "db/c2/log" "#@*&$!(*@)" :append true)
      (db/repair db "c2")
      (let [new-c2 (db/col db "c2")]
        (is (= (set (for [c (col/all new-c2)] (dissoc c :_pos))) #{{:c 3}}))
        (is (= (set (col/indexed new-c2)) #{[:a]})))
      ; open existing database
      (let [open-db (db/open "db")]
        (is (= (set (db/all open-db)) #{"c1" "c2"})))))
  (fs/rmrf (file "db")))