(ns Aurinko.hash-test
  (:require (Aurinko [hash :as hash]))
  (:use clojure.test
        [clojure.java.io :only [file]]))

(deftest last-bits
  (is (= (hash/last-bits 63 2) 3)))

(deftest hash-test
  (let [h (hash/new "hash" 1 2)]
    ; put entries
    (hash/kv h "a" 1)
    (hash/kv h "a" 2)
    (hash/kv h "a" 3)
    (hash/kv h "b" 4)
    ; grow hash table
    (hash/kv h "c" 5)
    (hash/kv h "d" 6)
    ; scan index in different ways
    (is (= (set (hash/k h "a" -1 (fn [_] true))) #{1 2 3}))
    (is (= (set (hash/k h "a" 2  (fn [_] true)))   #{1 2}))
    (is (= (set (hash/k h "a" -1 #(> % 1)))  #{2 3}))
    (is (= (set (hash/k h "d" -1 (fn [_] true))) #{6}))
    ; delete hash entries with limit and filter
    (hash/x h "a" 2 #(< % 3))
    (is (= (set (hash/k h "a" -1 (fn [_] true))) #{3}))
    (.delete (file "hash"))))