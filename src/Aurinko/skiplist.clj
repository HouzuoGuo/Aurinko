(ns Aurinko.skiplist
  (:import (java.io File RandomAccessFile))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer)))

(def ^:const FILE-HDR (int 8)) ; file header: number of levels, chance
(def ^:const PTR-SIZE (int 4)) ; every pointer in the file is an integer
(def ^:const NODE     (int 4)) ; every node has integer key and value

(defprotocol SkipListP
  (rand-lvl [this])
  (head     [this])
  (at-node  [this n])
  (node     [this n])
  (cut-lvl  [this lvl begin-from v])
  (cutlist  [this v])
  (v-after  [this v node top-lvl])
  (store    [this val])
  (get      [this val])
  (x        [this val]))

(deftype SkipList [path levels P fc ^{:unsynchronized-mutable true} file cmp-fun] SkipListP
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
        {:n n :v (.getInt file) :lvls (vec (map (fn [_] (.getInt file)) (range levels)))})
  (cut-lvl [this lvl begin-from v]
           (loop [matches (transient [])
                  prev-n begin-from
                  n begin-from]
             (let [node    (node this n)
                   lvl-ptr (nth (:lvls node) lvl)
                   cmp     (cmp-fun (:v node) v)]
               (cond
                 (= cmp -1)
                 (if (not= lvl-ptr -1)
                   (recur matches n lvl-ptr)
                   {:n n :matches (persistent! matches)})
                 (= cmp 0)
                 (if (not= lvl-ptr -1)
                   (recur (conj! matches (assoc node :prev-n prev-n)) n lvl-ptr)
                   {:n n :matches (persistent! (conj! matches (assoc node :prev-n prev-n)))})
                 (= cmp 1)
                 {:n prev-n :matches (persistent! matches)}))))
  (cutlist [this v]
           (loop [lvl (dec levels)
                  cut (transient [])
                  curr-node 0]
             (if (> lvl -1)
               (let [cut-lvl (cut-lvl this lvl curr-node v)]
                 (conj! cut cut-lvl)
                 (recur (dec lvl) cut (:n cut-lvl)))
               (persistent! cut))))
  (v-after [this v node top-lvl]
           (let [new-node-pos (.limit file)
                 new-node-num (quot (- (.limit file) FILE-HDR) (+ NODE (* levels PTR-SIZE)))]
             (set! file (.map fc FileChannel$MapMode/READ_WRITE
                          0 (+ (.limit file) (+ NODE (* PTR-SIZE levels)))))
             (.position file new-node-pos)
             (.putInt file v)
             (doseq [lvl (range top-lvl)]
               (at-node this node)
               (let [lvl-ptr-pos (+ (.position file) NODE (* PTR-SIZE lvl))]
                 (.position file lvl-ptr-pos)
                 (let [lvl-ptr (.getInt file)]
                   (.putInt (.position file lvl-ptr-pos) new-node-num)
                   (.putInt (.position file (+ new-node-pos NODE (* PTR-SIZE lvl))) lvl-ptr))))))
  (store [this val]
         (let [cut (cutlist this val)
               match (last (get this val))]
           (if (nil? match)
             (v-after this val (:n (last cut)) (rand-lvl this))
             (v-after this val (:n match) (- levels (count (filter zero? (:lvls match))))))))
  (get [this v]
       (flatten (for [cut-lvl (cutlist this v)]
                  (filter #(not (empty? %)) (:matches cut-lvl)))))
  (x [this val]
     (when-let [node (first (filter #(= (:v %) val) (get this val)))]
       (at-node this (:prev-n node))
       (.position file (+ (.position file) NODE))
       (doseq [lvl-ptr (:lvls node)]
         (.putInt file lvl-ptr)))))
       
(defn open [path cmp-fun]
  (let [fc   (.getChannel (RandomAccessFile. path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0 (.size fc))]
    (SkipList. path (int (.getInt file))
              (/ (int (.getInt file))) fc file cmp-fun)))

(defn new [path levels chance cmp-fun]
  (let [fc   (.getChannel (RandomAccessFile. path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0 (+ FILE-HDR NODE (* PTR-SIZE levels)))]
    (.putInt file levels)
    (.putInt file chance)
    (doseq [l (range (inc levels))]
      (.putInt file -1))
    (open path cmp-fun)))