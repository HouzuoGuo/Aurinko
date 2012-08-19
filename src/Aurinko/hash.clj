(ns Aurinko.hash
  (:require (Aurinko [fs :as fs])))

(def ^:const FILE-HDR 8) ; file header: number of key bits (int); entries per bucket (int)
(def ^:const BUK-HDR 4)  ; bucket header: next chained bucket number (int)
(def ^:const ENTRY 12)   ; entry: valid (int, 0 - deleted, 1 - valid); key (int); value (int)

(defn last-bits [^long int ^long n]
  (bit-and int (unchecked-dec (bit-shift-left 1 n))))

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

(deftype Hash [key-bits buk-size file ^{:unsynchronized-mutable true} num-buks] HashP
  (at    [this i] (fs/at file (+ FILE-HDR (* i (+ BUK-HDR (* ENTRY buk-size))))))
  (nextb [this i] ^int (fs/get-i (at this i)))
  (lastb [this i] ^int
         (loop [^int curr i
                ^int next (nextb this i)]
           (if (= next 0) curr (recur next (nextb this next))))) ; last buk has "next" set to 0
  (grow  [this i]
         (fs/put-i (at this (lastb this i)) num-buks)
         (fs/grow file (+ BUK-HDR (* ENTRY buk-size)))
         (set! num-buks (inc num-buks)) this)
  (save  [this] (fs/save file))
  (close [this] (fs/close file))
  (kv [this k v]
      (let [^int key-hash  (hash k)
            ^int first-buk (last-bits key-hash key-bits)
            ^int last-buk  (lastb this first-buk)]
        (loop [i 0
               buk first-buk]
          (let [entry (+ (fs/pos (at this buk))
                         BUK-HDR
                         (* ENTRY i))]
            (if (= (fs/get-i (fs/at file entry)) 0) ; find an empty entry
              (do
                (fs/put-i (fs/at file entry) 1)
                (fs/put-i file key-hash)
                (fs/put-i file v))
              (if (= i (dec buk-size))
                (if (= buk last-buk)
                  (kv (grow this last-buk) k v) ; grow when reaching last entry of last bucket
                  (recur 0 (nextb this buk)))
                (recur (inc i) buk)))))))
  (scan [this k limit proc filt]
        (let [^int key-hash  (hash k)
              ^int first-buk (last-bits key-hash key-bits)
              ^int last-buk  (lastb this first-buk)]
          (loop [count 0
                 result '()
                 i 0
                 buk first-buk]
            (if (or (= count limit) ; stop when reached the limit or last entry
                    (and (= i buk-size) (= buk last-buk)))
              result
              (let [entry         (+ (fs/pos (at this buk)) BUK-HDR (* ENTRY i))
                    ^int valid    (fs/get-i (fs/at file entry))
                    ^int key      (fs/get-i file)
                    ^int val      (fs/get-i file)]
                (if (and (= valid 1) (= key-hash key) (filt val))
                  (do
                    (proc entry)
                    (if (= i buk-size)
                      ; go to next bucket if processed last entry of current bucket
                      (let [^int next-buk (nextb this buk)]
                        (if (> next-buk buk)
                          (recur (inc count) (conj result val) 0 next-buk)
                          (throw (Exception. (str "Index file " (fs/path file) " is corrupted, repair collection please")))))
                      (recur (inc count) (conj result val) (inc i) buk)))
                  (if (= i buk-size)
                    (let [^int next-buk (nextb this buk)]
                      (if (> next-buk buk)
                        (recur count result 0 next-buk)
                        (throw (Exception. (str "Index file " (fs/path file) " is corrupted, repair collection please")))))
                    (recur count result (inc i) buk))))))))
  (k [this k limit filt] (scan this k limit (fn [_]) filt))
  (x [this k limit filt] (scan this k limit #(fs/put-i (fs/at file %) 0) filt)))

(defn open [path]
  (let [f (fs/open path)
        ^int key-bits (fs/get-i f)
        ^int buk-size (fs/get-i f)]
    (Hash. key-bits buk-size f (quot (- (fs/limit f) FILE-HDR)
                                     (+ BUK-HDR (* buk-size ENTRY))))))

(defn new [path key-bits buk-size]
  (let [new-file (fs/grow (fs/open path)
                          (+ FILE-HDR (* (Math/pow 2 key-bits)
                                         (+ BUK-HDR (* buk-size ENTRY)))))]
    (fs/put-i new-file key-bits)
    (fs/put-i new-file buk-size)
    (open path)))
