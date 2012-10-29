(ns Aurinko.hash
  (:import (java.io File RandomAccessFile))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer)))

(def ^:const FILE-HDR (int 12)) ; file header: new bucket position, number of key bits, entries per bucket (integers)
(def ^:const BUK-HDR  (int 4))  ; bucket header: next chained bucket number (int)
(def ^:const ENTRY    (int 12)) ; entry: valid (int, 0 - deleted, 1 - valid); key (int); value (int)
(def ^:const GROW (int 33554432)) ; grow index file by 32MB when necessary

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

(deftype Hash [path key-bits buk-size ^FileChannel fc
               ^{:unsynchronized-mutable true} ^MappedByteBuffer file
               ^{:unsynchronized-mutable true} num-buks] HashP
  (at    [this i] (.position file (+ FILE-HDR (* i (+ BUK-HDR (* ENTRY buk-size))))))
  (nextb [this i] (int (.getInt ^MappedByteBuffer (at this i))))
  (lastb [this i]
         (loop [curr (int i)
                next (int (nextb this i))]
           (if (= next 0) curr (recur next (int (nextb this next)))))) ; last buk has "next" set to 0
  (grow  [this i]
         (.putInt ^MappedByteBuffer (at this (lastb this i)) num-buks)
         (set! num-buks (inc num-buks))
         (let [new-buk-pos (do (.position file 0) (.getInt file))
               buk-size (+ BUK-HDR (* ENTRY buk-size))]
           (when (>= (+ buk-size new-buk-pos) (.limit file))
             (set! file (.map fc FileChannel$MapMode/READ_WRITE
                          0 (+ (.limit file) GROW)))
             (.position file 0)
             (.putInt file (+ buk-size new-buk-pos))))
         this)
  (save  [this] (.force fc false))
  (close [this] (save this) (.close fc))
  (kv [this k v]
      (let [key-hash  (int (hash k))
            first-buk (int (last-bits key-hash key-bits))
            last-buk  (int (lastb this first-buk))]
        (loop [i   (int 0)
               buk (int first-buk)]
          (let [entry (+ (.position ^MappedByteBuffer (at this buk))
                         BUK-HDR
                         (* ENTRY i))]
            (if (= (.getInt ^MappedByteBuffer (.position file entry)) 0) ; find an empty entry
              (do
                (.putInt ^MappedByteBuffer (.position file entry) 1)
                (.putInt file key-hash)
                (.putInt file v))
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
                      valid (int (.getInt ^MappedByteBuffer (.position file entry)))
                      key   (int (.getInt file))
                      val   (int (.getInt file))]
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
  (x [this k limit filt] (scan this k limit #(.putInt ^MappedByteBuffer (.position file %) 0) filt)))

(defn open [^String path]
  (let [fc   (.getChannel (RandomAccessFile. path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0 (.size fc))
        new-buk-pos (int (.getInt file))
        key-bits    (int (.getInt file))
        buk-size    (int (.getInt file))]
    (Hash. path key-bits buk-size fc file (quot (- new-buk-pos FILE-HDR) (+ BUK-HDR (* buk-size ENTRY))))))

(defn new [^String path key-bits buk-size]
  (let [fc   (.getChannel (RandomAccessFile. path "rw"))
        file (.map fc FileChannel$MapMode/READ_WRITE 0
               (+ FILE-HDR (* (Math/pow 2 key-bits)
                              (+ BUK-HDR (* buk-size ENTRY)))))]
    (.putInt file (.limit file)) ; new bucket position
    (.putInt file key-bits)
    (.putInt file buk-size)
    (open path)))