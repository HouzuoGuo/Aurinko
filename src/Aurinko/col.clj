(ns Aurinko.col
  (:use [clojure.java.io :only [file]])
  (:require (Aurinko [hash :as hash] [fs :as fs]) [clojure.string :as cstr])
  (:import (java.io File RandomAccessFile PrintWriter BufferedWriter FileWriter))
  (:import (java.nio.channels FileChannel FileChannel$MapMode)
           (java.nio MappedByteBuffer)))

(def ^:const COL-HDR (int 4)) ; collection header - next insert pos
(def ^:const DOC-HDR (int 8)) ; document header - valid (0 or 1), allocated room
(def ^:const DOC-MAX  (int 1048576))  ; maximum document size - 1MB
(def ^:const COL-GROW (int 33554432)) ; grow collection by 32MB when necessary
(def ^:const EOF (int -1))

(defn file2index [^File f]
  (let [path (read-string
               (cstr/replace
                 (first (cstr/split (.getName f) #"\.")) ; get only the name, not extension
                 \! \:))] ; transform e.g. [!a !b] into [:a :b]
    (if (vector? path)
      {:path path :hash (hash/open (.getAbsolutePath f))}
      (throw (Exception. (str "not a valid index path: " path))))))

(defn index2filename [path]
  (if (vector? path)
    (str (cstr/replace (pr-str path) \: \!) ".index")
    (throw (Exception. (str "index path " path " has to be a vector")))))

(defprotocol ColP
  (insert       [this doc])
  (update       [this doc])
  (delete       [this doc])
  (index-doc    [this doc i] "Index a single document on index i")
  (unindex-doc  [this doc]   "Remove a document from indexes")
  (index-path   [this path]  "Index a document path (e.g. [:unix :bsd :freeBSD :ports])")
  (unindex-path [this path]  "Remove an indexed path")
  (indexed      [this]       "Return all indexed paths")
  (index        [this path]  "Return the index object for the path")
  (by-pos       [this pos]   "Fetch the document at position pos")
  (all          [this fun]   "Apply the fun to all documents")
  (save         [this]       "Flush buffer to disk")
  (close        [this]))

(deftype Col [dir data-fc log
              ^{:unsynchronized-mutable true} data
              ^{:unsynchronized-mutable true} indexes] ColP
  (insert [this doc]
          (if (map? doc)
            (do
              (let [limit    (int (.limit ^MappedByteBuffer data))
                    pos      (int (do (.position ^MappedByteBuffer data 0) (.getInt ^MappedByteBuffer data)))
                    pos-doc  (assoc doc :_pos pos)
                    text     (pr-str pos-doc)
                    length   (int (.length text))
                    room     (int (+ length length))] ; leave 2x room for the doc
                (when (> length DOC-MAX)
                  (throw (Exception. (str "document is too large (> " DOC-MAX " bytes"))))
                (when (< limit (+ room pos))
                  (set! data (.map ^FileChannel data-fc FileChannel$MapMode/READ_WRITE
                               0 (+ (.limit ^MappedByteBuffer data) COL-GROW))))
                (.position ^MappedByteBuffer data pos)
                (.putInt   ^MappedByteBuffer data 1) ; valid
                (.putInt   ^MappedByteBuffer data room) ; allocated room
                (.put      ^MappedByteBuffer data (.getBytes text)) ; document
                (.put      ^MappedByteBuffer data (byte-array length)) ; padding
                (.position ^MappedByteBuffer data 0) ; write next insert position
                (.putInt   ^MappedByteBuffer data (+ pos DOC-HDR room))
                (doseq [i indexes] (index-doc this pos-doc i)))
              (.println ^PrintWriter log (str "[:i " doc "]")))
            (throw (Exception. (str "document" doc "has to be a map")))))
  (update [this doc]
          (let [pos (:_pos doc)]
            (when pos
              (.position ^MappedByteBuffer data (+ 4 pos))
              (let [room  (int (.getInt ^MappedByteBuffer data))
                    text  (pr-str doc)
                    size  (int (.length text))]
                (if (> room size)
                  (do ; overwrite the document
                    (unindex-doc this (by-pos this pos))
                    (.position ^MappedByteBuffer data (+ DOC-HDR pos))
                    (.put ^MappedByteBuffer data (.getBytes text))
                    (.put ^MappedByteBuffer data (byte-array (- room size)))
                    (doseq [i indexes] (index-doc this doc i))
                    (.println ^PrintWriter log (str "[:u " doc "]")))
                  (do (delete this doc) (insert this doc))))))) ; re-insert if no enough room left
  (delete [this doc]
          (let [pos (:_pos doc)]
            (when pos
              (.position ^MappedByteBuffer data pos)
              (.putInt   ^MappedByteBuffer data 0) ; set valid to 0 - deleted
              (unindex-doc this doc)
              (.println ^PrintWriter log (str "[:d " pos "]")))))
  (index-doc [this doc i]
             (let [val      (get-in doc (:path i))
                   to-index (if (vector? val) val [val])]
               (when val
                 (doseq [v to-index] ; index everything inside a vector
                   (hash/kv (:hash i) v (:_pos doc))))))
  (unindex-doc [this doc]
               (doseq [i indexes]
                 (let [val     (get-in doc (:path i))
                       indexed (if (vector? val) val [val])
                       doc-pos (int (:_pos doc))]
                   (doseq [v indexed] (hash/x (:hash i) v 1 #(= % doc-pos))))))
  (index-path [this path]
              (let [filename (str dir (index2filename path))]
                (if (or (nil? filename) (.exists (file filename)))
                  (throw (Exception. (str path " is an invalid path or already indexed")))
                  (let [new-index {:path path :hash (hash/new filename 12 100)}]
                    (set! indexes (conj indexes new-index))
                    (all this #(index-doc this % new-index)))))) ; index all docs on the path
  (unindex-path [this path]
                (let [filename (str dir (index2filename path))]
                  (if (.exists (file filename))
                    (if (.delete (file filename))
                      (set! indexes (remove #(= path (:path %)) indexes))
                      (throw (Exception. (str "failed to delete index file" filename))))
                    (throw (Exception. (str "cannot find index" path))))))
  (indexed [this] (for [i indexes] (:path i)))
  (index   [this path] (:hash (first (filter #(= (:path %) path) indexes))))
  (by-pos  [this pos]
           (try
             (.position ^MappedByteBuffer data (int pos))
             (let [valid (int (.getInt ^MappedByteBuffer data))
                   room  (int (.getInt ^MappedByteBuffer data))]
               (if (and (= valid 0) (= room 0))
                 EOF
                 (do
                   (when (> room DOC-MAX)
                     (throw (Exception. (str "collection " dir " is corrupted, please repair collection"))))
                   (let [text (byte-array room)]
                     (.get ^MappedByteBuffer data ^bytes text)
                     (if (= 1 valid)
                       (read-string (String. (byte-array (remove zero? text)))))))))
             (catch Exception e (.printStackTrace e))))
  (all [this fun]
       (loop [pos COL-HDR]
         (.position ^MappedByteBuffer data pos)
         (let [doc (by-pos this pos)
               next-pos (.position ^MappedByteBuffer data)]
           (when-not (= doc EOF)
             (when-not (or (nil? doc) (empty? doc)) (fun doc))
             (recur next-pos)))))
  (save  [this]
         (doseq [i indexes] (hash/save (:hash i)))
         (.force ^FileChannel data-fc false)
         (.flush ^PrintWriter log))
  (close [this]
         (save this)
         (doseq [i indexes] (hash/close (:hash i)))
         (.close ^FileChannel data-fc)
         (.close ^PrintWriter log)))

(defn open [path]
  (let [fc (.getChannel (RandomAccessFile. ^String (str path (File/separator) "data") "rw"))]
    (Col. (str path (File/separator))
          fc
          (PrintWriter. (BufferedWriter. (FileWriter. (str path (java.io.File/separator) "log") true)))
          (do (let [map (.map fc FileChannel$MapMode/READ_WRITE 0 (max (.size fc) COL-HDR))]
                (when (= (.getInt ^MappedByteBuffer map) 0) ; new collection data file?
                  (.position ^MappedByteBuffer map 0)
                  (.putInt ^MappedByteBuffer map COL-HDR)) ; write the correct next insert pos
                map))
          (remove nil? (map file2index (fs/findre path #".*\.index"))))))