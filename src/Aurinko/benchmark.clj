(ns Aurinko.benchmark
  (:use [clojure.java.io :only [file]])
  (:require (Aurinko [hash :as hash] [col :as col] [fs :as fs] [query :as query])))

(defn -main [& args]
  (Thread/sleep 5000)
  (.mkdir (file "col0"))
  (.mkdir (file "col1"))
  (let [col0 (col/open "col0")
        col1 (col/open "col1")]
    (prn "Warming up..")
    (doseq [v (range 20000)] (col/insert col0 {:tag v}))
    (doseq [v (col/all col0)] (col/update col0 (assoc v :tag2 v)))
    (doseq [v (col/all col0)] (col/delete col0 v))

    (let [h (hash/new "hash" 12 100)]
      (prn "Hash - put 200k entries")
      (time (doseq [v (range 200000)] (hash/kv h v v)))
      (prn "Hash - get 200k entries")
      (time (doseq [v (range 200000)] (hash/k h v 1 (fn [_] true))))
      (prn "Hash - invalidate 200k entries")
      (time (doseq [v (range 200000)] (hash/x h v 1 (fn [_] true))))
      (.delete (file "hash")))
    (prn)

    (prn "Collection - insert 20k documents (3 indexes)")
    (col/index-path col1 [:thing1])
    (col/index-path col1 [:thing2])
    (col/index-path col1 [:a1 :a2 :a3])
    (time (doseq [v (range 20000)]
            (col/insert col1 {:a1 {:a2 {:a3 (rand-int 20000)}}
                              :thing1 (str (rand-int 20000))
                              :thing2 (rand-int 20000)
                              :map {:complex {:data (rand-int 20000)}}
                              :action ["insert" "benchmark"]
                              :purpose "benchmark"})))
    (prn "Collection - update 20k documents (3 indexes)")
    (time (doseq [v (col/all col1)] (col/update col1 (assoc v :action "u"))))
    (prn "Collection - update 20k documents (3 indexes, grow each document)")
    (time
      (doseq [v (col/all col1)]
        (col/update col1 (assoc v :extra "123456789012345678901234567890123456789012345678901234567890"))))
    (prn "Collection - read all 20k documents")
    (time (col/all col1))
    (prn "Collection - index 20k documents")
    (time (col/index-path col1 [:map :complex :data]))
    (prn "Query - index lookup 20k items")
    (time (doseq [v (range 20000)]
            (let [val (rand-int 20000)]
              (query/q col1 [:col [:map :complex :data] val -1 :eq]))))
    (prn "Query - scan collection (no index)")
    (time (query/q col1 [:col [:action] "insert" -1 :eq]))
    (prn "Query - complex (no index)")
    (time (query/q col1 [:col [:purpose] "benchmark" -1 :eq
                         [:map :complex :not :exist] :not-have
                         :col [:map :complex :data]  10000 -1 :ge
                         [:a1 :a2 :a3] 10000 -1 :lt
                         :diff]))
    (time (query/q col1 [:col [:action] "insert" -1 :eq
                         :col [:map :complex :data]  10000 -1 :gt
                         [:map :complex :data] :has
                         [:a1 :a2 :a3] 10000 -1 :lt
                         :union]))
    (time (query/q col1 [:col [:action] "benchmark" -1 :eq
                         :col [:map :complex :data]  10000 -1 :gt
                         [:a1 :a2 :a3] 10000 -1 :lt
                         :intersect
                         [:a1 :a2 :a3] :asc]))
    (prn "Collection - delete 20k documents")
    (time (doseq [v (col/all col1)] (col/delete col1 v))))
  (fs/rmrf (file "col0"))
  (fs/rmrf (file "col1")))