(ns Aurinko.skiplist
  (:import (java.io File RandomAccessFile))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer)))

(def ^:const FILE-HDR (int 8))  ; file header: number of levels, chance
(def ^:const PTR-SIZE (int 4))  ; every pointer in the file is an integer
(def ^:const NODE     (int 4))  ; every node has integer key and value
(def ^:const NIL      (int -1)) ; nil pointer

(defprotocol SkipListP
  (at [this node-num])
  (node-at [this node-num])
  (cut-lvl [this v lvl begin-from])
  (insert [this v]))

(deftype SkipList [path levels P fc ^{:unsynchronized-mutable true} file cmp-fun] SkipListP
  (at [this node-num]
      (.position file (+ FILE-HDR (* node-num (+ NODE (* PTR-SIZE levels))))))
  (node-at [this node-num]
           (at this node-num)
           {:n node-num :v (.getInt file) :lvls (vec (map (fn [_] (.getInt file)) (range levels)))})
  (cut-lvl [this v lvl begin-from]
           (loop [curr-node-num begin-from
                  prev-node (node-at this begin-from)
                  matches   (transient [])]
             (if (= curr-node-num NIL)
                 {:prev prev-node :node prev-node :matches (persistent! matches)}
                 (let [curr-node (node-at this curr-node-num)
                       next-node-num (nth (:lvls curr-node) lvl)
                       cmp-result (cmp-fun (:v curr-node) v)]
                   (cond
                     (= cmp-result -1)
                     (recur next-node-num curr-node matches)
                     (= cmp-result 0)
                     (recur next-node-num curr-node (conj! matches curr-node))
                     (= cmp-result 1)
                     {:prev prev-node :node curr-node :matches (persistent! matches)})))))
  (insert [this v]
          (prn "insert" v)
          (let [empty-list? (= (.limit file) FILE-HDR)
                new-node-pos (.limit file)
                new-node-num (quot (- (.limit file) FILE-HDR) (+ NODE (* levels PTR-SIZE)))]
            (set! file (.map fc FileChannel$MapMode/READ_WRITE
                         0 (+ (.limit file) NODE (* PTR-SIZE levels))))
            (if empty-list?
              (do
                (.position file FILE-HDR)
                (.putInt file v)
                (doseq [i (range levels)]
                  (.putInt file NIL)))
              (let [top-lvl (loop [lvl 0]
                              (if (and (< lvl (dec levels)) (< (Math/random) P))
                                (recur (inc lvl))
                                lvl))]
                (.position file new-node-pos)
                (.putInt file v)
                (doseq [v (range levels)]
                  (.putInt file NIL))
                (loop [lvl top-lvl
                       node-num 0]
                  (when (> lvl -1)
                    (let [lvl-cut (cut-lvl this v lvl node-num)
                          last-lvl-node (:n (:node lvl-cut))]
                      (at this last-lvl-node)
                      (let [ptr-pos (+ (.position file) NODE (* PTR-SIZE lvl))
                            old-node-num (do (.position file ptr-pos) (.getInt file))]
                        (prn "last node" (:node lvl-cut) "at level" lvl "old pointer" old-node-num "new pointer" new-node-num)
                        (.position file ptr-pos)
                        (.putInt file new-node-num)
                        (.position file (+ new-node-pos NODE (* PTR-SIZE lvl)))
                        (.putInt file old-node-num))
                      (recur (dec lvl) last-lvl-node)))))))))
       
(defn open [path cmp-fun]
  (let [fc   (.getChannel (RandomAccessFile. path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0 (.size fc))]
    (SkipList. path (int (.getInt file))
              (/ (.getInt file)) fc file cmp-fun)))

(defn new [path levels chance cmp-fun]
  (let [fc   (.getChannel (RandomAccessFile. path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0 FILE-HDR)]
    (.putInt file levels)
    (.putInt file chance)
    (open path cmp-fun)))