(ns Aurinko.query
  (:require (Aurinko [col :as col] [hash :as hash]))
  (:use clojure.set))

(defn doc-match? [doc path match]
  "Return true if the document contains the match in the path"
  (let [val (get-in doc path)]
    (or (= val match)
        (and (vector? val) (some #(= % match) val)))))

(defn check-args [op & args]
  "Throw an exception if any of the args is nil"
  (when (some nil? args) (throw (Exception. (str args": no enough arguments for op " op)))))

(defn scan-eq
  "Lookup in a collection/result set"
  ([col stack]
    (let [[limit val path source & _] stack
          index-scan (fn [] (hash/k (col/index col path) val limit
                                    #(doc-match? (col/by-pos col %) path val)))] ; avoid hash collision
      (check-args :eq limit val path source)
      (cons (set
              (cond
                (= source :col)
                (if (nil? (col/index col path))
                  (let [result  (persistent!
                                  (let [filtered (transient [])]
                                    (col/all col #(when (doc-match? % path val)
                                                    (conj! filtered (:_pos %))))
                                    filtered))]
                    (if (= limit -1)
                      result
                      (take limit result)))
                  (index-scan))
                (set? source)
                (if (nil? (col/index col path))
                  (let [result (filter #(doc-match? (col/by-pos col %) path val)
                                       source)]
                    (if (= limit -1)
                      result
                      (take limit result)))
                  (intersection source (index-scan)))
                :else
                (throw (Exception. (str "Expecting " source " to be :col or set")))))
            (drop 4 stack))))
  ([_ col stack]
    (scan-eq col stack)))

(defn scan-ineq [op col stack]
  "Range query in a collection/result set"
  (let [[limit val path source & _] stack]
    (check-args op val path source)
    (cons (set (cond
                 (= source :col)
                 (let [result (persistent!
                                (let [filtered (transient [])]
                                  (col/all col
                                           #(let [doc-val (get-in % path)]
                                              (when (and (not (nil? doc-val))
                                                         (op (compare doc-val val) 0))
                                                (conj! filtered (:_pos %)))))
                                  filtered))]
                   (if (= limit -1)
                     result
                     (take limit result)))
                 (set? source)
                 (filter #(let [doc-val (get-in (col/by-pos col %) path)]
                            (and (not (nil? doc-val))
                                 (op (compare doc-val val) 0)))
                         source)
                 :else
                 (throw (Exception. (str "Expecting " source " to be :col or set")))))
          (drop 4 stack))))

(defn two-sets [op col stack]
  "Set operations"
  (let [[s1 s2 & _] stack]
    (check-args op s1 s2)
    (cons (op (if (= s1 :col)
                (set (persistent!
                       (let [all-docs (transient [])]
                         (col/all col #(conj! all-docs (:_pos %)))
                         all-docs)))
                s1)
              (if (= s2 :col)
                (set (persistent!
                       (let [all-docs (transient [])]
                         (col/all col #(conj! all-docs (:_pos %)))
                         all-docs)))
                s2))
          (drop 2 stack))))

(defn path-check [op col stack]
  "Scan for path existence/inexistence in result set"
  (let [[path source & _] stack]
    (check-args op path source)
    (cons (set (cond
                 (= source :col)
                 (persistent!
                   (let [filtered (transient [])]
                     (col/all col #(when (op (get-in % path))
                                     (conj! filtered (:_pos %))))
                     filtered))
                 (set? source)
                 (filter #(op (get-in (col/by-pos col %) path)) source)
                 :else
                 (throw (Exception. (str "Expecting " source " to be :col or set")))))
          (drop 2 stack))))

(defn sorted [op col stack]
  "Sort result set by path"
  (let [[path source & _] stack
        sorted
        (sort-by val
                 (into {}
                       (cond
                         (= source :col)
                         (seq (persistent!
                                (let [pairs (transient [])]
                                  (col/all col #(conj! pairs [(:_pos %) (get-in % path)]))
                                  pairs)))
                         (set? source)
                         (for [pos source]
                           [pos (get-in (col/by-pos col pos) path)])
                         :else
                         (throw (Exception. (str "Expecting " source " to be :col or set"))))))]
    (check-args op path source)
    (cons (keys (if (= op <=) sorted (reverse sorted)))
          (drop 2 stack))))

(defn col2set [_ col stack]
  "Put all document positions into a set and push to the stack"
  (cons (set (persistent!
               (let [all-docs (transient [])]
                 (col/all col #(conj! all-docs (:_pos %)))
                 all-docs))) stack))

(defn q [col conds]
  (loop [stack     '()
         remaining conds]
    (let [thing (first remaining)]
      (if (nil? thing) (vec stack)
        (recur
          (if (and (keyword? thing) (not= thing :col))
            ((case thing
               :eq                       scan-eq
               (:ne :ge :gt :le :lt)     scan-ineq
               (:diff :intersect :union) two-sets
               (:has :not-have)          path-check
               (:asc :desc)              sorted
               :all                      col2set
               (throw (Exception. (str thing ": not understood"))))
              (thing {:eq scan-eq :ge >= :gt > :le <= :lt < :ne not=
                      :diff difference :intersect intersection :union union
                      :has #(not (nil? %)) :not-have nil?
                      :asc <= :desc >=}) col stack)
            (cons thing stack))
          (rest remaining))))))