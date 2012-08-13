(ns Aurinko.query-test
  (:require (Aurinko [col :as col] [query :as query] [fs :as fs]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(.mkdir (file "col"))
(.createNewFile (file "col/data"))
(.createNewFile (file "col/log"))
(def data (col/open "col"))
(col/insert data {:map1 {:val 123 :vec [1 2 3]} :score 2})
(col/insert data {:id 1 :name "Howard"      :age "young" :likes ["Clojure" "sailing" "OOP" "AFL"] :score 1})
(col/insert data {:id 2 :name "Bruce Eckel" :age "young" :likes ["OOP" "Python"]   :score 3})
(col/insert data {:id 3 :name "Bobby"       :age "very young" :likes ["AFL" "NRL"]})
(col/index-path data [:id])
(col/index-path data [:likes])
(def col-pos (for [doc (col/all data)] (:_pos doc)))

(deftest test-eq
  (is (query/doc-match? (col/by-pos data (first col-pos)) [:map1 :val] 123))
  (is (query/doc-match? (col/by-pos data (first col-pos)) [:map1 :vec] 1))
  (is (query/doc-match? (col/by-pos data (first col-pos)) [:map1 :vec] 2))
  (is (query/doc-match? (col/by-pos data (first col-pos)) [:map1 :vec] 3))
  (is (not (query/doc-match? (col/by-pos data (first col-pos)) [:map1 :vec] 4))))

(deftest test-q
  ; invert stack
  (is (= (query/q data [:col [1 2 3] {:a 1 :b 2} "abc" 1 2 3]) (reverse [:col [1 2 3] {:a 1 :b 2} "abc" 1 2 3])))
  ; collection scan equal
  (is (= (query/q data [:col [:age] "young" -1 :eq]) [#{(nth col-pos 1) (nth col-pos 2)}]))
  ; index scan equal
  (is (= (query/q data [:col [:likes] "AFL" -1 :eq]) [#{(nth col-pos 1) (nth col-pos 3)}]))
  ; index scan equal then set scan
  (is (= (query/q data [:col [:likes] "Clojure" -1 :eq
                        [:age]   "young"   -1 :eq]) [#{(nth col-pos 1)}]))
  ; collection scan equal then index scan and set intersection
  (is (= (query/q data [:col [:age]   "young"   -1 :eq
                        [:likes] "Clojure" -1 :eq]) [#{(nth col-pos 1)}]))
  ; compare ge
  (is (= (query/q data [:col [:id] 2 -1 :ge]) [#{(nth col-pos 2) (nth col-pos 3)}]))
  (is (= (query/q data [(set col-pos) [:id] 2 -1 :ge]) [#{(nth col-pos 2) (nth col-pos 3)}]))
  ; compare gt
  (is (= (query/q data [:col [:id] 1 -1 :gt]) [#{(nth col-pos 2) (nth col-pos 3)}]))
  (is (= (query/q data [(set col-pos) [:id] 1 -1 :gt]) [#{(nth col-pos 2) (nth col-pos 3)}]))
  ; compare le
  (is (= (query/q data [:col [:id] 2 -1 :le]) [#{(nth col-pos 1) (nth col-pos 2)}]))
  (is (= (query/q data [(set col-pos) [:id] 2 -1 :le]) [#{(nth col-pos 1) (nth col-pos 2)}]))
  ; compare lt
  (is (= (query/q data [:col [:id] 3 -1 :lt]) [#{(nth col-pos 1) (nth col-pos 2)}]))
  (is (= (query/q data [(set col-pos) [:id] 3 -1 :lt]) [#{(nth col-pos 1) (nth col-pos 2)}]))
  ; comapre ne
  (is (= (query/q data [:col [:id] 2 -1 :ne]) [#{(nth col-pos 1) (nth col-pos 3)}]))
  (is (= (query/q data [(set col-pos) [:id] 2 -1 :ne]) [#{(nth col-pos 1) (nth col-pos 3)}]))
  ; diff
  (is (= (query/q data [:col [:id] 2 -1 :le :col [:id] 2 -1 :ge :diff]) [#{(nth col-pos 3)}]))
  ; intersect
  (is (= (query/q data [:col [:id] 1 -1 :gt :col [:id] 3 -1 :lt :intersect]) [#{(nth col-pos 2)}]))
  ; union
  (is (= (query/q data [:col [:id] 2 -1 :gt :col [:id] 2 -1 :lt :union]) [#{(nth col-pos 1) (nth col-pos 3)}]))
  ; has
  (is (= (query/q data [:col [:id] :has]) [#{(nth col-pos 1) (nth col-pos 2) (nth col-pos 3)}]))
  ; not-have
  (is (= (query/q data [:col [:id] :not-have]) [#{(nth col-pos 0)}]))
  ; sort descending
  (is (= (query/q data [:col [:score] :desc]) [[(nth col-pos 2) (nth col-pos 0) (nth col-pos 1) (nth col-pos 3)]]))
  ; sort ascending
  (is (= (query/q data [:col [:score] :asc]) [[(nth col-pos 3) (nth col-pos 1) (nth col-pos 0) (nth col-pos 2)]]))
  ; all
  (is (= (query/q data [:all]) [(set col-pos)])))
(fs/rmrf (file "col"))