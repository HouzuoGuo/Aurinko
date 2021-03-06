(ns Aurinko.col
  (:use [clojure.java.io :only [file]])
  (:require (Aurinko [hash :as hash] [fs :as fs]) [clojure.string :as cstr])
  (:import (java.io File RandomAccessFile PrintWriter FileWriter))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer BufferUnderflowException)))

(def ^:const COL-HDR (int 4)) ; collection header - next insert pos
(def ^:const DOC-HDR (int 8)) ; document header - valid (0 or 1), allocated room
(def ^:const DOC-MAX (int 1048576)) ; maximum document size - 1MB
(def ^:const GROW (int 33554432)) ; grow collection by 32MB when necessary
(def ^:const EOF (int -1))

(defn index2filename [path] "Make a filename for the index path"
  (if (vector? path)
    (str (cstr/replace (pr-str path) \: \!) ".hash")
    (throw (Exception. (str "index path " path " has to be a vector")))))

(defprotocol ColP
  (open-indexes [this] "Open all index files")
  (insert       [this doc])
  (update       [this doc])
  (delete       [this doc])
  (unindex-doc  [this doc]  "Remove a document from indexes")
  (unindex-path [this path] "Remove an indexed path")
  (indexed      [this]      "Return all hash indexed paths")
  (by-pos       [this pos]  "Fetch the document at position pos")
  (all          [this fun]  "Apply the fun to all documents")
  (number-docs  [this]      "Count number of documents in collection")
  (save         [this]      "Flush data and log to disk")
  (close        [this])
  (index        [this path] "Return the index object for the path")
  (index-path   [this path] "Index a document path (e.g. [:unix :bsd :freeBSD :ports])")
  (hash-index-doc  [this doc i]  "Index a single document on hash index i"))

(deftype Col [dir ^FileChannel data-fc ^PrintWriter log
              ^{:unsynchronized-mutable true} ^MappedByteBuffer data
              ^{:unsynchronized-mutable true} hashes] ColP
  (open-indexes [this]
                (set! hashes (remove nil? (map (fn [^java.io.File file]
                                                 (let [path (read-string
                                                              (cstr/replace
                                                                (first (cstr/split (.getName file) #"\.")) ; index file name (no ext name)
                                                                \! \:))]
                                                   {:path path :index (hash/open (.getAbsolutePath file))}))
                                               (fs/findre dir #".*\.hash")))))
  (insert [this doc]
          (if (map? doc)
            (do
              (let [limit    (int (.limit data))
                    pos      (int (do (.position data 0) (.getInt data)))
                    pos-doc  (assoc doc :_pos pos)
                    text     (pr-str pos-doc)
                    length   (int (.length text))
                    room     (int (+ length length))] ; leave 2x room for the doc
                (when (> length DOC-MAX)
                  (throw (Exception. (str "document is too large (> " DOC-MAX " bytes"))))
                (when (< limit (+ room pos))
                  (set! data (.map data-fc FileChannel$MapMode/READ_WRITE
                               0 (+ (.limit data) GROW))))
                (.position data pos)
                (.putInt   data 1) ; valid
                (.putInt   data room) ; allocated room
                (.put      data (.getBytes text)) ; document
                (.put      data (byte-array length)) ; padding
                (.position data 0) ; write next insert position
                (.putInt   data (+ pos DOC-HDR room))
                (doseq [i hashes] (hash-index-doc  this pos-doc i)))
              (.println log (str "[:i " doc "]")))
            (throw (Exception. (str "document '" doc "' has to be a map")))))
  (update [this doc]
          (when-let [pos (:_pos doc)]
            (.position data (+ 4 pos))
            (let [room  (int (.getInt data))
                  text  (pr-str doc)
                  size  (int (.length text))]
              (when (> size DOC-MAX)
                (throw (Exception. (str "document is too large (> " DOC-MAX " bytes"))))
              (if (> room size)
                (do ; overwrite the document
                  (unindex-doc this (by-pos this pos))
                  (.position data (+ DOC-HDR pos))
                  (.put data (.getBytes text))
                  (.put data (byte-array (- room size)))
                  (doseq [i hashes] (hash-index-doc  this doc i))
                  (.println log (str "[:u " doc "]")))
                (do (delete this doc) (insert this doc)))))) ; re-insert if no enough room left
  (delete [this doc]
          (when-let [pos (:_pos doc)]
            (.position data pos)
            (.putInt   data 0) ; set valid to 0 - deleted
            (unindex-doc this doc)
            (.println log (str "[:d " pos "]"))))
  (hash-index-doc [this doc i]
                  (when-let [val (get-in doc (:path i))]
                    (doseq [v (if (vector? val) val [val])] ; index everything inside a vector
                      (hash/kv (:index i) v (:_pos doc)))))
  (unindex-doc [this doc]
               (doseq [i hashes]
                 (let [val     (get-in doc (:path i))
                       doc-pos (int (:_pos doc))]
                   (doseq [v (if (vector? val) val [val])] (hash/x (:index i) v 1 #(= % doc-pos))))))
  (index-path [this path]
              (let [filename (str dir (index2filename path))]
                (if (or (not (vector? path)) (.exists (file filename)))
                  (throw (Exception. (str path " is an invalid path or already indexed")))
                  (let [new-index {:path path :index (hash/new filename 14 100)}]
                    (set! hashes (conj hashes new-index))
                    (all this #(hash-index-doc this % new-index))))))
  (unindex-path [this path]
                (let [hash-index (str dir (index2filename path))]
                  (when (.exists (file hash-index))
                    (.delete (file hash-index))
                    (set! hashes (remove #(= path (:path %)) hashes)))))
  (indexed [this] (for [i hashes] (:path i)))
  (index [this path] (:index (first (filter #(= (:path %) path) hashes))))
  (by-pos  [this pos]
           (try
             (.position data (int pos))
             (let [valid (int (.getInt data))
                   room  (int (.getInt data))]
               (if (and (= valid 0) (= room 0))
                 EOF
                 (do
                   (when (> room DOC-MAX)
                     (throw (Exception. (str "collection " dir " could be corrupted, repair collection?"))))
                   (let [text (byte-array room)]
                     (.get data text)
                     (when (= 1 valid)
                       (read-string (String. (byte-array (remove zero? text)))))))))
             (catch BufferUnderflowException e EOF)
             (catch Exception e (.printStackTrace e))))
  (all [this fun]
       (loop [pos (int COL-HDR)]
         (.position data pos)
         (let [doc      (by-pos this pos)
               next-pos (int (.position data))]
           (when-not (= doc EOF)
             (when-not (or (nil? doc) (empty? doc))
               (fun doc))
             (recur next-pos)))))
  (number-docs [this]
               (try
                 (.position data COL-HDR)
                 (loop [number (int 0)]
                   (let [valid (int (.getInt data))
                         room  (int (.getInt data))]
                     (if (and (= valid 0) (= room 0))
                       number
                       (do
                         (when (> room DOC-MAX)
                           (throw (Exception. (str "collection " dir " could be corrupted, repair collection?"))))
                         (if (< (+ (.position data) room) (.limit data))
                           (do
                             (.position data (+ (.position data) room))
                             (recur (if (= valid 1) (inc number) number)))
                           number)))))
                 (catch Exception e (.printStackTrace e))))
  (save  [this]
         (doseq [i hashes] (hash/save (:index i)))
         (.force data-fc false)
         (.flush log))
  (close [this]
         (save this)
         (doseq [i hashes] (hash/close (:index i)))
         (.close data-fc)
         (.close log)))

(defn open [path]
  (let [fc (.getChannel (RandomAccessFile. (str path (File/separator) "data") "rw"))
        the-col (Col. (str path (File/separator)) fc
                      (PrintWriter. (FileWriter. (str path (java.io.File/separator) "log") true))
                      (do (let [map (.map fc FileChannel$MapMode/READ_WRITE 0 (max (.size fc) COL-HDR))]
                            (when (= (.getInt map) 0) ; new collection data file?
                              (.position map 0)
                              (.putInt map COL-HDR)) ; write the correct next insert pos
                            map))
                      nil)]
    (open-indexes the-col)
    the-col))