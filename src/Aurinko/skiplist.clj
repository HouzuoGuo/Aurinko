(ns Aurinko.skiplist
  (:import (java.io File RandomAccessFile))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer)))

(def ^:const FILE-HDR (int 8)) ; file header: number of levels, chance
(def ^:const PTR-SIZE (int 4)) ; every pointer in the file is an integer
(def ^:const ENTRY    (int 12)) ; every entry has integer validity, key and value

(defprotocol SkipListP
  (cut-lvl [this k cmp lvl ptr])
  (cut [this k cmp])
  (kv [this k v cmp])
  (x [this k cmp])
  (k [this k cmp]))

(deftype SkipList [path levels chance fc ^{:unsynchronized-mutable true} file] SkipListP
  (cut-lvl [this k cmp lvl ptr]
           (loop [result '()
                  prev-ptr ptr
                  curr-ptr ptr]
             (if (= curr-ptr 0)
               result
               (do
                 (.position ^MappedByteBuffer file curr-ptr)
                 (let [valid   (.getInt ^MappedByteBuffer file)
                       key     (.getInt ^MappedByteBuffer file)
                       val     (.getInt ^MappedByteBuffer file)
                       lvl-ptr (.getInt ^MappedByteBuffer (.position ^MappedByteBuffer file (+ ptr (* lvl PTR-SIZE))))]
                   (if (= valid 1)
                     (cond
                       (= key k)
                       (recur (conj result {:prev-ptr prev-ptr :ptr curr-ptr :val val :lvl lvl})
                              curr-ptr lvl-ptr)
                       (< key k)
                       (recur result curr-ptr lvl-ptr)
                       (> key k)
                       (if (empty? result)
                         (conj result {:ptr prev-ptr})
                         result))
                     (recur result curr-ptr lvl-ptr)))))))
  (cut [this k cmp]
       (loop [lvl-results '()
              lvl (dec levels)
              ptr (int 0)]
         (let [lvl-result (cut-lvl this k cmp lvl ptr)]
           (if (or (= lvl 0) (empty? lvl-results))
             lvl-results
             (recur (conj lvl-results lvl-result) (dec levels) (int (:ptr (first lvl-results))))))))
             
  (kv [this k v cmp]
      (let [pos (int (.limit ^MappedByteBuffer file))]
        (set! file (.map ^FileChannel fc FileChannel$MapMode/READ_WRITE
                     0 (+ (.limit ^MappedByteBuffer file) (+ ENTRY (* PTR-SIZE levels)))))
        (.position ^MappedByteBuffer file pos)
        (.putInt ^MappedByteBuffer file 1)
        (.putInt ^MappedByteBuffer file k)
        (.putInt ^MappedByteBuffer file v)
        (let [cutlist (cut this k cmp)
              duplicate (remove nil? (for [[lvl lvl-result] (map-indexed cutlist)]
                                       (when (filter #(contains? % :val) lvl-result)
                                         lvl)))
              insert-fun (fn [lvl ptr]
                           (let [lvl-pos (+ ptr (* PTR-SIZE lvl))
                                 lvl-ptr-val (.getInt ^MappedByteBuffer (.position ^MappedByteBuffer file lvl-pos))]
                             (.putInt ^MappedByteBuffer (.position ^MappedByteBuffer file (+ pos (* PTR-SIZE lvl))) lvl-ptr-val) ; new->next = old->next
                             (.putInt ^MappedByteBuffer (.position ^MappedByteBuffer file lvl-pos) pos)))] ; old->next = new
          (if duplicate
            (doseq [[lvl lvl-cut] (map-indexed cutlist)]
              (insert-fun lvl (:ptr (last lvl-cut))))
            (loop [lvl 0
                   level-chance (double 1)]
              (when (< (Math/random) level-chance)
                (insert-fun lvl (:ptr (last (nth cutlist lvl))))
                (recur (inc lvl) (* level-chance chance))))))))
  (x [this k cmp]
     (doseq [lvl (cut this k cmp)]
       (doseq [entry (filter #(contains? % :val) lvl)]
         (let [{:keys [prev-ptr ptr lvl]} entry
               next-ptr (.getInt ^MappedByteBuffer (.position ^MappedByteBuffer file (+ ptr (* lvl PTR-SIZE))))]
           (.putInt ^MappedByteBuffer (.position ^MappedByteBuffer file (+ prev-ptr ENTRY (* lvl PTR-SIZE))) next-ptr)))))
  (k [this k cmp]
     (doseq [lvl (cut this k cmp)]
       (loop [result lvl]
         (let [found (first result)]
           (if (contains? found :val)
             (.getInt ^MappedByteBuffer (.position ^MappedByteBuffer file (+ 8 (:ptr found))))
             (recur (rest result))))))))

(defn open [path]
  (let [fc   (.getChannel (RandomAccessFile. ^String path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0 (.size fc))]
    (SkipList. path (int (.getInt ^MappedByteBuffer file))
              (quot 1 (int (.getInt ^MappedByteBuffer file))) fc file)))

(defn new [path levels chance]
  (let [fc   (.getChannel (RandomAccessFile. ^String path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0 (+ FILE-HDR (* PTR-SIZE levels)))]
    (.putInt ^MappedByteBuffer file levels)
    (.putInt ^MappedByteBuffer file chance)
    (open path)))