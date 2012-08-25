(ns Aurinko.skiplist
  (:import (java.io File RandomAccessFile))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer)))

(def ^:const FILE-HDR (int 8)) ; file header: number of levels, chance
(def ^:const PTR-SIZE (int 4)) ; every pointer in the file is an integer
(def ^:const ENTRY    (int 12)) ; every entry has integer validity, key and value

(defprotocol SkipListP
  (cut [this k cmp])
  (kv [this k v cmp])
  (x [this k cmp])
  (k [this k cmp]))

(deftype SkipList [path levels chance fc ^{:unsynchronized-mutable true} file] SkipListP
  (cut [this k cmp]
       (doall
         (map (fn [level]
                (.position ^MappedByteBuffer file
                  (+ FILE-HDR ENTRY (* level PTR-SIZE)))
                (loop [result '()
                       prev-ptr 0
                       ptr (int (.getInt ^MappedByteBuffer file))]
                  (prn "prev-ptr" prev-ptr "ptr" ptr "result" result)
                  (if (= ptr 0)
                    result
                    (do
                      (.position ^MappedByteBuffer file ptr)
                      (let [valid    (int (.getInt ^MappedByteBuffer file))
                            key      (int (.getInt ^MappedByteBuffer file))
                            val      (int (.getInt ^MappedByteBuffer file))
                            next-ptr (int (.getInt ^MappedByteBuffer (.position ^MappedByteBuffer file
                                                                       (+ (.position ^MappedByteBuffer file) (* level PTR-SIZE)))))]
                        (if (= valid 1)
                          (let [comparison (cmp key k)]
                            (cond
                              (> comparison 0)
                              (conj result {:ptr prev-ptr :match false})
                              (= comparison 0)
                              (recur (conj result {:prev-ptr prev-ptr :ptr ptr :match true :level level}) ptr next-ptr)
                              :else
                              (recur result ptr next-ptr)))
                          (recur result ptr next-ptr)))))))
              (range levels))))
  (kv [this k v cmp]
      (let [pos (.limit ^MappedByteBuffer file)]
        (set! file (.map ^FileChannel fc FileChannel$MapMode/READ_WRITE
                     0 (+ (.limit ^MappedByteBuffer file) (+ ENTRY (* PTR-SIZE levels)))))
        (.position ^MappedByteBuffer file pos)
        (.putInt ^MappedByteBuffer file 1)
        (.putInt ^MappedByteBuffer file k)
        (.putInt ^MappedByteBuffer file v)
        (let [cutlist (cut this k cmp)]
          (loop [level 0
                 level-chance (double 1)]
            (when (< (Math/random) level-chance)
              (let [entry (+ (:ptr (nth cutlist level)) (* PTR-SIZE level))
                    ptr-value (.getInt ^MappedByteBuffer (.position ^MappedByteBuffer file entry))]
                (.putInt ^MappedByteBuffer (.position ^MappedByteBuffer file entry) pos)
                (.putInt ^MappedByteBuffer (.position ^MappedByteBuffer file (+ pos (* PTR-SIZE level))) ptr-value))
              (recur (inc level) (* level-chance chance)))))))
  (x [this k cmp]
     (doseq [lvl (cut this k cmp)]
       (doseq [entry (filter #(:match %) lvl)]
         (let [{:keys [prev-ptr ptr level]} entry
               next-ptr (.getInt ^MappedByteBuffer (.position ^MappedByteBuffer file (+ ptr (* level PTR-SIZE))))]
           (.putInt ^MappedByteBuffer (.position ^MappedByteBuffer file (+ prev-ptr ENTRY (* level PTR-SIZE))) next-ptr)))))
  (k [this k cmp]
     (doseq [lvl (cut this k cmp)]
       (loop [result lvl]
         (let [found (first result)]
           (if (:match found)
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