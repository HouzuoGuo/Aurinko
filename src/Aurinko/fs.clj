(ns Aurinko.fs
  (:use [clojure.java.io :only [file reader]])
  (:import (java.io RandomAccessFile))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer)))

(def ^:const sep (java.io.File/separator))

(defn findre [dir pattern]
  (doall (filter #(re-matches pattern (.getName ^java.io.File %)) (file-seq (file dir)))))

(defn ls [dir]
  (map #(.getName ^java.io.File %) (.listFiles (file dir))))

(defn rmrf [^java.io.File dir]
  (if (.isDirectory dir)
    (doseq [v (.list dir)] (rmrf (file dir v))))
  (.delete dir))

(defn lines [path proc]
  (try
    (with-open [r (reader path)]
      (doseq [line (line-seq r)] (proc line)))
    (catch Exception e (.printStackTrace e))))