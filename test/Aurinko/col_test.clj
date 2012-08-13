(ns Aurinko.col-test
  (:require (Aurinko [col :as col] [hash :as hash] [fs :as fs]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(deftest file2index
  (let [name "[!a !b !c].index"
        hash (hash/new name 1 1)
        tmp (file name)
        index (col/file2index tmp)]
    (is (= (:path index) [:a :b :c]))
    (is (not (nil? (:hash index))))
    (.delete tmp)))

(deftest index2filename
  (is (= (col/index2filename [:a :b :c]) "[!a !b !c].index")))

(deftest col
  (.mkdir (file "col"))
  (.createNewFile (file "col/data"))
  (.createNewFile (file "col/log"))
  (let [h1 (hash/new "col/[!a !b].index" 4 4)
        h2 (hash/new "col/[!c].index" 4 4)]
    (let [c (col/open "col")]
      ; insert - indexed
      (col/insert c {:a {:b [1 2]} :c 3})
      (col/insert c {:a {:b [4 5]} :c 6})
      ; insert - not indexed
      (col/insert c {:foo {:bar "spam"}})
      ; read all
      (is (= (for [doc (col/all c)] (dissoc doc :_pos))
             [{:a {:b [1 2]} :c 3}
              {:a {:b [4 5]} :c 6}
              {:foo {:bar "spam"}}]))
      ; index working?
      (let [first-doc (:_pos (first (col/all c)))
            second-doc (:_pos (second (col/all c)))]
        (is (= (set (hash/k h1 1 -1 (fn [_] true))) #{first-doc}))
        (is (= (set (hash/k h1 2 -1 (fn [_] true))) #{first-doc}))
        (is (= (set (hash/k h2 3 -1 (fn [_] true))) #{first-doc}))
        (is (= (set (hash/k h1 4 -1 (fn [_] true))) #{second-doc}))
        (is (= (set (hash/k h1 5 -1 (fn [_] true))) #{second-doc}))
        (is (= (set (hash/k h2 6 -1 (fn [_] true))) #{second-doc})))
      ; update - no grow
      (col/update c (assoc (first (col/all c)) :a {:b [8 9]}))
      (col/update c (dissoc (second (col/all c)) :a))
      ; update and grow
      (col/update c (assoc (nth (col/all c) 2) :extra "abcdefghijklmnopqrstuvwxyz0123456789"))
      (is (= (for [doc (col/all c)] (dissoc doc :_pos))
             [{:a {:b [8 9]} :c 3}
              {:c 6}
              {:foo {:bar "spam"} :extra "abcdefghijklmnopqrstuvwxyz0123456789"}]))
      ; index updated?
      (let [first-doc (:_pos (first (col/all c)))
            second-doc (:_pos (second (col/all c)))]
        (is (= (set (hash/k h1 1 -1 (fn [_] true))) #{})) ; became 8
        (is (= (set (hash/k h1 2 -1 (fn [_] true))) #{})) ; became 9
        (is (= (set (hash/k h2 3 -1 (fn [_] true))) #{first-doc}))
        (is (= (set (hash/k h1 4 -1 (fn [_] true))) #{})) ; removed
        (is (= (set (hash/k h1 5 -1 (fn [_] true))) #{})) ; removed
        (is (= (set (hash/k h2 6 -1 (fn [_] true))) #{second-doc}))
        (is (= (set (hash/k h1 8 -1 (fn [_] true))) #{first-doc}))
        (is (= (set (hash/k h1 9 -1 (fn [_] true))) #{first-doc})))
      ; remove index
      (col/unindex-path c [:a :b])
      (is (not (.exists (file "col/[!a !b].index"))))
      ; make a new index
      (col/index-path c [:a])
      (col/insert c {:a 1})
      (let [first-doc (:_pos (first (col/all c)))
            last-doc (:_pos (last (col/all c)))
            new-index (col/index c [:a])]
        (is (= (set (hash/k new-index {:b [8 9]} -1 (fn [_] true))) #{first-doc}))
        (is (= (set (hash/k new-index 1          -1 (fn [_] true))) #{last-doc})))
      (fs/rmrf (file "col")))))