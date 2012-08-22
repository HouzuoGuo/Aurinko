(ns Aurinko.hash
  (:import (java.io File RandomAccessFile))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer)))

(def ^:const FILE-HDR (int 8)) ; file header: number of key bits (int); entries per bucket (int)
(def ^:const BUK-HDR (int 4))  ; bucket header: next chained bucket number (int)
(def ^:const ENTRY (int 12))   ; entry: valid (int, 0 - deleted, 1 - valid); key (int); value (int)

(defn last-bits [integer n]
  (bit-and integer (unchecked-dec (bit-shift-left 1 n))))

(defprotocol HashP
  (at    [this i]   "Handle moves to bucket i")
  (nextb [this i]   "Return next bucket number in chain i")
  (lastb [this i]   "Return last bucket number in chain i")
  (grow  [this i]   "Grow a bucket in chain i")
  (save  [this]     "Flush buffer to disk")
  (close [this]     "Close hash file")
  (kv    [this k v] "Store key-value pair")
  (k     [this k limit filt]      "Find no more than limit filtered entries of key k")
  (x     [this k limit filt]      "Invalidate no more than limit filtered entries of key k")
  (scan  [this k limit proc filt] "Scan entries of key k, process no more than limit filtered entries by proc"))

(deftype Hash [path key-bits buk-size fc
               ^{:unsynchronized-mutable true} file
               ^{:unsynchronized-mutable true} num-buks] HashP
  (at    [this i] (.position ^MappedByteBuffer file (+ FILE-HDR (* i (+ BUK-HDR (* ENTRY buk-size))))))
  (nextb ^int [this i] (int (.getInt ^MappedByteBuffer (at this i))))
  (lastb ^int [this i]
         (loop [curr (int i)
                next (int (nextb this i))]
           (if (= next 0) curr (recur next (int (nextb this next)))))) ; last buk has "next" set to 0
  (grow  [this i]
         (.putInt ^MappedByteBuffer (at this (lastb this i)) num-buks)
         (set! file (.map ^FileChannel fc FileChannel$MapMode/READ_WRITE
                      0 (+ (.limit ^MappedByteBuffer file) (+ BUK-HDR (* ENTRY buk-size)))))
         (set! num-buks (inc num-buks)) this)
  (save  [this] (.force ^FileChannel fc false))
  (close [this] (save this) (.close ^FileChannel fc))
  (kv [this k v]
      (let [key-hash  (int (hash k))
            first-buk (int (last-bits key-hash key-bits))
            last-buk  (int (lastb this first-buk))]
        (loop [i   (int 0)
               buk (int first-buk)]
          (let [entry (+ (.position ^MappedByteBuffer (at this buk))
                         BUK-HDR
                         (* ENTRY i))]
            (if (= (.getInt ^MappedByteBuffer (.position ^MappedByteBuffer file entry)) 0) ; find an empty entry
              (do
                (.putInt ^MappedByteBuffer (.position ^MappedByteBuffer file entry) 1)
                (.putInt ^MappedByteBuffer file key-hash)
                (.putInt ^MappedByteBuffer file v))
              (if (= i (dec buk-size))
                (if (= buk last-buk)
                  (kv (grow this last-buk) k v) ; grow when reaching last entry of last bucket
                  (recur 0 (int (nextb this buk))))
                (recur (inc i) buk)))))))
  (scan [this k limit proc filt]
        (let [key-hash  (int (hash k))
              first-buk (int (last-bits key-hash key-bits))
              last-buk  (int (lastb this first-buk))]
          (loop [count (int 0)
                 result '()
                 i (int 0)
                 buk (int first-buk)]
            (if (or (= count limit) ; stop when reached the limit or last entry
                    (and (= i buk-size) (= buk last-buk)))
              result
              (do
                (let [entry (+ (.position ^MappedByteBuffer (at this buk)) BUK-HDR (* ENTRY i))
                      valid (int (.getInt ^MappedByteBuffer (.position ^MappedByteBuffer file entry)))
                      key   (int (.getInt ^MappedByteBuffer file))
                      val   (int (.getInt ^MappedByteBuffer file))]
                  (if (and (= valid 1) (= key-hash key) (filt val))
                    (do
                      (proc entry)
                      (if (= i buk-size)
                        ; go to next bucket if processed last entry of current bucket
                        (let [next-buk (int (nextb this buk))]
                          (if (> next-buk buk)
                          (recur (inc count) (conj result val) 0 next-buk)
                          (throw (Exception. (str "index " path " is corrupted, please repair collection")))))
                        (recur (inc count) (conj result val) (inc i) buk)))
                    (if (= i buk-size)
                      (let [next-buk (int (nextb this buk))]
                        (if (> next-buk buk)
                          (recur count result 0 next-buk)
                          (throw (Exception. (str "index " path " is corrupted, please repair collection")))))
                      (recur count result (inc i) buk)))))))))
  (k [this k limit filt] (scan this k limit (fn [_]) filt))
  (x [this k limit filt] (scan this k limit #(.putInt ^MappedByteBuffer (.position ^MappedByteBuffer file %) 0) filt)))

(defn open [path]
  (let [fc   (.getChannel (RandomAccessFile. ^String path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0 (.size fc))
        key-bits (int (.getInt ^MappedByteBuffer file))
        buk-size (int (.getInt ^MappedByteBuffer file))]
    (Hash. path key-bits buk-size fc file (quot (- (.limit ^MappedByteBuffer file) FILE-HDR)
                                                (+ BUK-HDR (* buk-size ENTRY))))))

(defn new [path key-bits buk-size]
  (let [fc   (.getChannel (RandomAccessFile. ^String path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0
               (+ FILE-HDR (* (Math/pow 2 key-bits)
                              (+ BUK-HDR (* buk-size ENTRY)))))]
    (.putInt ^MappedByteBuffer file key-bits)
    (.putInt ^MappedByteBuffer file buk-size)
    (open path)))