(ns Aurinko.col
  (:use [clojure.java.io :only [file]])
  (:require (Aurinko [fs :as fs] [hash :as hash])
            [clojure.string :as cstr])
  (:import (java.io File)))

(def DOC-HDR 8) ; document header: valid (int, 0 - deleted, 1 - valid); allocated room (int)

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
  (all          [this]       "Return a sequence of all non-deleted documents")
  (save         [this]       "Flush buffer to disk")
  (close        [this]))

(deftype Col [dir data log ^{:unsynchronized-mutable true} indexes] ColP
  (insert [this doc]
          (if (map? doc)
            (do 
              (let [^int pos (fs/limit data)
                    pos-doc  (assoc doc :_pos pos)
                    text     (pr-str pos-doc)
                    length   (.length text)
                    room     (+ length length)] ; leave 2x room for the doc
                (fs/grow  data (+ DOC-HDR room))
                (fs/at    data pos)
                (fs/put-i data 1) ; valid
                (fs/put-i data room) ; allocated room
                (fs/put-b data (.getBytes text)) ; document
                (fs/put-b data (byte-array length)) ; padding
                (doseq [i indexes] (index-doc this pos-doc i)))
              (spit log (str "[:i " doc "]\n") :append true))
            (str "document" doc "has to be a map")))
  (update [this doc]
          (let [^int pos (:_pos doc)]
            (if pos
              (let [^int room (fs/get-i (fs/adv (fs/at data pos) 4)) ; skip valid flag (an int)
                    text (pr-str doc)
                    size (.length text)]
                (if (> room size)
                  (do
                    (unindex-doc this (by-pos this pos))
                    ; overwritten the document
                    (fs/adv (fs/at data pos) 8)
                    (fs/put-b data (.getBytes text))
                    (fs/put-b data (byte-array (- room size)))
                    (doseq [i indexes] (index-doc this doc i))
                    (spit log (str "[:u " doc "]\n") :append true))
                  (do (delete this doc) (insert this doc))))))) ; no enough room? re-insert
  (delete [this doc]
          (let [^int pos (:_pos doc)]
            (when pos
              (fs/put-i (fs/at data pos) 0) ; set valid to 0 - deleted
              (unindex-doc this doc)
              (spit log (str "[:d " pos "]\n") :append true))))
  (index-doc [this doc i]
             (let [val      (get-in doc (:path i))
                   to-index (if (vector? val) val [val])]
               (if val
                 (doseq [v to-index] ; index everything inside a vector
                   (hash/kv (:hash i) v (:_pos doc))))))
  (unindex-doc [this doc]
               (doseq [i indexes]
                 (let [val     (get-in doc (:path i))
                       indexed (if (vector? val) val [val])
                       ^int doc-pos (:_pos doc)]
                   (doseq [v indexed] (hash/x (:hash i) v 1 #(= % doc-pos))))))
  (index-path [this path]
              (let [filename (str dir (index2filename path))]
                (if (or (nil? filename) (.exists (file filename)))
                  (throw (Exception. (str path " is an invalid path or already indexed")))
                  (let [new-index {:path path :hash (hash/new filename 12 100)}]
                    (set! indexes (conj indexes new-index))
                    (doseq [doc (all this)] (index-doc this doc new-index)))))) ; index all docs on the path
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
             (let [^int   valid (fs/get-i   (fs/at data pos))
                   ^bytes text  (byte-array (fs/get-i data))]
               (fs/get-b data text)
               (if (= 1 valid) (read-string (String. (byte-array (remove zero? text))))))
             (catch java.nio.BufferUnderflowException e -1) ; EOF
             (catch Exception e (.printStackTrace e) {})))
  (all [this]
       (fs/at data 0)
       (doall (remove nil? (take-while #(not= % -1) (repeatedly #(by-pos this (fs/pos data)))))))
  (save  [this]
         (doseq [i indexes] (hash/save (:hash i)))
         (fs/save data))
  (close [this]
         (doseq [i indexes] (hash/close (:hash i)))
         (fs/close data)))

(defn open [path]
  (Col. (str path fs/sep)
        (fs/open (str path fs/sep "data"))
        (str path fs/sep "log")
        (remove nil? (map file2index (fs/findre path #".*\.index")))))
