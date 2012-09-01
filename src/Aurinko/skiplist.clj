(ns Aurinko.skiplist
  (:import (java.io File RandomAccessFile))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer)))

(def ^:const FILE-HDR (int 8)) ; file header: number of levels, chance
(def ^:const PTR-SIZE (int 4)) ; every pointer in the file is an integer
(def ^:const NODE    (int 8)) ; every NODE has integer key and value

(defprotocol SkipListP
  (rand-lvl [this])
  (head     [this])
  (at-node  [this n])
  (node     [this n])
  (cut-lvl  [this lvl begin-from k])
  (cutlist  [this k])
  (kv-after [this k v node top-lvl])
  (kv       [this k v])
  (k        [this k]))

(deftype SkipList [path levels P fc ^{:unsynchronized-mutable true} file] SkipListP
  (rand-lvl [this]
            (loop [lvl 1]
              (if (and (< lvl levels) (< (Math/random) P))
                (recur (inc lvl))
                lvl)))
  (head [this]
        (.position file FILE-HDR)
        (vec (map (.getInt file) (range levels))))
  (at-node [this n]
           (.position file (+ FILE-HDR
                             (* n (+ (* PTR-SIZE levels) NODE)))))
  (node [this n]
        (at-node this n)
        {:n n :k (.getInt file) :v (.getInt file) :lvls (vec (map (fn [_] (.getInt file)) (range levels)))})
  (cut-lvl [this lvl begin-from k]
           (loop [matches (transient [])
                  prev-n begin-from
                  n begin-from]
             (let [node (node this n)
                   lvl-ptr (nth (:lvls node) lvl)]
               (cond
                 (< (:k node) k)
                 (if (not= lvl-ptr 0)
                   (recur matches n lvl-ptr)
                   {:n n :matches (persistent! matches)})
                 (= (:k node) k)
                 (if (not= lvl-ptr 0)
                   (recur (conj! matches node) n lvl-ptr)
                   {:n n :matches (persistent! (conj! matches node))})
                 (> (:k node) k)
                 {:n prev-n :matches (persistent! matches)}))))
  (cutlist [this k]
           (loop [lvl (dec levels)
                  cut (transient [])
                  curr-node 0]
             (if (> lvl -1)
               (let [cut-lvl (cut-lvl this lvl curr-node k)]
                 (conj! cut cut-lvl)
                 (recur (dec lvl) cut (:n cut-lvl)))
               (persistent! cut))))
  (kv-after [this k v node top-lvl]
            (let [new-node-pos (.limit file)
                  new-node-num (quot (- (.limit file) FILE-HDR) (+ NODE (* levels PTR-SIZE)))]
              (set! file (.map fc FileChannel$MapMode/READ_WRITE
                           0 (+ (.limit file) (+ NODE (* PTR-SIZE levels)))))
              (.position file new-node-pos)
              (.putInt file k)
              (.putInt file v)
              (doseq [lvl (range top-lvl)]
                (at-node this node)
                (let [lvl-ptr-pos (+ (.position file) NODE (* PTR-SIZE lvl))]
                  (.position file lvl-ptr-pos)
                  (let [lvl-ptr (.getInt file)]
                    (.putInt (.position file lvl-ptr-pos) new-node-num)
                    (.putInt (.position file (+ new-node-pos NODE (* PTR-SIZE lvl))) lvl-ptr))))))
  (kv [this key val]
      (let [cut (cutlist this key)
            match (first (k this key))]
        (if (nil? match)
          (kv-after this key val (:n (first cut)) (rand-lvl this))
          (kv-after this key val (:n match) (- levels (count (filter zero? (:lvls match))))))))
  (k [this k]
     (flatten (for [cut-lvl (cutlist this k)]
                (filter #(not (empty? %)) (:matches cut-lvl))))))
  
(defn open [path]
  (let [fc   (.getChannel (RandomAccessFile. path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0 (.size fc))]
    (SkipList. path (int (.getInt file))
              (/ (int (.getInt file))) fc file)))

(defn new [path levels chance]
  (let [fc   (.getChannel (RandomAccessFile. path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0 (+ FILE-HDR NODE (* PTR-SIZE levels)))]
    (.putInt file levels)
    (.putInt file chance)
    (doseq [l (range (+ 2 levels))]
      (.putInt file 0))
    (open path)))