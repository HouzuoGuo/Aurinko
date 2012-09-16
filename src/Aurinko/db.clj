(ns Aurinko.db
  (:use [clojure.java.io :only [file]])
  (:require (Aurinko [fs :as fs] [col :as col])))

(defprotocol DbP
  (create   [this name])
  (rename   [this old new])
  (delete   [this name])
  (compress [this name] "Remove space used by deleted documents")
  (repair   [this name] "Recreate the collection from its log file")
  (col      [this name])
  (all      [this])
  (save     [this])
  (close    [this]))

(deftype Db [dir ^{:unsynchronized-mutable true} cols] DbP
  (create [this name]
          (save this)
          (when ((keyword name) cols)
            (throw (Exception. (str "collection " name " already exists"))))
          (if (.mkdirs (file (str dir fs/sep name)))
            (set! cols (assoc cols (keyword name) (col/open (str dir fs/sep name))))
            (throw (Exception. (str "failed to create directory for collection " name)))))
  (rename [this old new]
          (save this)
          (when ((keyword new) cols)
            (throw (Exception. (str "new collection name " new " is already used"))))
          (when-not ((keyword old) cols)
            (throw (Exception. (str "collection " old " does not exist"))))
          (when-not (.renameTo (file dir old) (file dir new))
            (throw (Exception. (str "failed to rename collection directory"))))
          (set! cols (assoc (dissoc cols (keyword old))
                            (keyword new) (col/open (str dir fs/sep new)))))
  (delete [this name]
          (save this)
          (when-not ((keyword name) cols)
            (throw (Exception. (str "collection " name " does not exist"))))
          (fs/rmrf (file dir name))
          (set! cols (dissoc cols (keyword name))))
  (compress [this name]
            (save this)
            (if-let [c ((keyword name) cols)]
              (let [hash-indexed  (col/indexed c :hash)
                    range-indexed (col/indexed c :range)
                    tmp-name (str (System/nanoTime))
                    tmp      (do (create this tmp-name) ((keyword tmp-name) cols))]
                (col/all ((keyword name) cols) #(col/insert tmp %))
                (delete this name)
                (rename this tmp-name name)
                (let [repaired (col this name)]
                  (doseq [i hash-indexed]  (col/index-path repaired i :hash))
                  (doseq [i range-indexed] (col/index-path repaired i :range))))
              (throw (Exception. (str "collection " name " does not exist")))))
  (repair [this name]
          (save this)
          (if-let [c ((keyword name) cols)]
            (let [hash-indexed  (col/indexed c :hash)
                  range-indexed (col/indexed c :range)
                  tmp-name (str (System/nanoTime))
                  tmp      (do (create this tmp-name) (col this tmp-name))]
              (fs/lines (str dir fs/sep name fs/sep "log")
                        (fn [line]
                          (try
                            (let [entry       (read-string line)
                                  [op info _] entry]
                              (condp = op
                                :i (col/insert tmp info)
                                :u (col/update tmp info)
                                :d (col/delete tmp {:_pos info})))
                            (catch Exception e (prn "Failed to repair from log line" line)))))
              (delete this name)
              (rename this tmp-name name)
              (let [repaired (col this name)]
                (doseq [i hash-indexed]  (col/index-path repaired i :hash))
                (doseq [i range-indexed] (col/index-path repaired i :range))))
            (throw (Exception. (str "collection " name " does not exist")))))
  (col   [this name]
         (when-not (contains? cols (keyword name))
           (throw (Exception. (str "collection " name " does not exist"))))
         ((keyword name) cols))
  (all   [this] (fs/ls dir))
  (save  [this] (doseq [c (vals cols)] (col/save c)))
  (close [this] (save this) (doseq [c (vals cols)] (col/close c))))

(defn open [path]
  (when-not (.exists (file path))
    (.mkdir (file path)))
  (Db. (str path fs/sep)
       (into {} (for [f (fs/ls path)]
                  [(keyword f) (col/open (str path fs/sep f))]))))