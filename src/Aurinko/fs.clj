(ns Aurinko.fs
  (:use [clojure.java.io :only [file reader]])
  (:import (java.io RandomAccessFile))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer ByteBuffer)))

(def sep (java.io.File/separator))

(defn findre [dir pattern]
  (doall (filter #(re-matches pattern (.getName ^java.io.File %)) (file-seq (file dir)))))

(defn ls [dir]
  (map #(.getName ^java.io.File %) (.listFiles (file dir))))

(defn rmrf [^java.io.File dir]
  (if (.isDirectory dir)
    (doseq [v (.list dir)] (rmrf (file dir v))))
  (.delete dir))

(defprotocol FileP
  (at    [this i]  "Handle moves to i")
  (adv   [this i]  "Advance handle by i")
  (pos   [this]    "Return handle position")
  (get-i [this]    "Read an integer")
  (put-i [this i]  "Write an integer")
  (get-b [this b]  "Read into byte buffer")
  (put-b [this b]  "Write from the buffer")
  (limit [this]    "Return buffer size limit")
  (grow  [this by] "Grow file by specified size")
  (save  [this]    "Flush buffer to disk")
  (close [this]    "Close file"))

(deftype File [fc ^{:unsynchronized-mutable true} buf] FileP
  (at    [this i]    (.position ^MappedByteBuffer buf ^int i) this)
  (adv   [this i]    (.position ^MappedByteBuffer buf (unchecked-add (.position ^MappedByteBuffer buf) ^int i)) this)
  (pos   ^int [this] (.position ^MappedByteBuffer buf))
  (get-i ^int [this] (.getInt   ^MappedByteBuffer buf))
  (put-i [this i]    (.putInt ^MappedByteBuffer buf ^int i))
  (get-b [this b]    (.get ^MappedByteBuffer buf ^bytes b) this)
  (put-b [this b]    (.put ^MappedByteBuffer buf ^bytes b))
  (limit ^int [this] (.limit ^MappedByteBuffer buf))
  (grow  [this by]   (set! buf (.map ^FileChannel fc FileChannel$MapMode/READ_WRITE
                                 0 (unchecked-add (limit this) ^int by))) this)
  (save  [this]      (.force ^FileChannel fc false))
  (close [this]      (.close ^FileChannel fc)))

(defn open [path]
  (let [fc (.getChannel (new RandomAccessFile ^String path "rw"))]
    (File. fc (.map fc FileChannel$MapMode/READ_WRITE 0 (.size fc)))))

(defn lines [path proc]
  (try
    (with-open [r (reader path)]
      (doseq [line (line-seq r)] (proc line)))
    (catch Exception e "")))
