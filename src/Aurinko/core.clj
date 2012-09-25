(ns Aurinko.core
  (:require (Aurinko [socket :as socket] [db :as db] [col :as col] [query :as query]))
  (:import (java.io BufferedReader InputStreamReader OutputStream PrintWriter))
  (:import (java.util.concurrent LinkedBlockingQueue))
  (:import (java.util Timer TimerTask)))

(def Q (LinkedBlockingQueue. 10000)) ; request queue
(def db (atom nil))
(def ^:const OK {:ok true})

(defn work [item]
  (let [[out cmd & args] item]
    (binding [*out* out]
      (try
        (case cmd
          ; DB management
          :save     (do (db/save @db) (prn OK))
          :create   (do (db/create @db (first args)) (prn OK))
          :rename   (do (db/rename @db (first args) (second args)) (prn OK))
          :drop     (do (db/delete @db (first args)) (prn OK))
          :compress (do (db/compress @db (first args)) (prn OK))
          :repair   (do (db/repair @db (first args)) (prn OK))
          :all      (do (prn (vec (db/all @db))))
          :stop     (do (db/close @db) (prn OK) (System/exit 0))
          ; Document management
          :findall (do (prn (let [all-docs (transient [])]
                              (col/all (db/col @db (first args)) #(conj! all-docs %))
                              (persistent! all-docs))))
          :insert (do (col/insert (db/col @db (first args)) (second args)) (prn OK))
          :update (let [[name func & conds] args
                        evaled (eval func)]
                    (let [col (db/col @db name)]
                      (doseq [result (query/q col conds)]
                        (doseq [pos result]
                          (col/update col (evaled (col/by-pos col pos))))))
                    (prn OK))
          :delete (let [[name & conds] args
                        col (db/col @db name)]
                    (doseq [result (query/q col conds)]
                      (doseq [pos result]
                        (col/delete col {:_pos pos})))
                    (prn OK))
          :fastupdate (let [[name & docs] args
                            col (db/col @db name)]
                        (doseq [doc docs] (col/update col doc))
                        (prn OK))
          :fastdelete (let [[name & poses] args
                            col (db/col @db name)]
                        (doseq [pos poses] (col/delete col {:_pos pos}))
                        (prn OK))
          ; Query
          :q (let [[name & conds] args]
               (prn (vec (query/q (db/col @db (first args)) conds))))
          :select (prn (vec (let [[name & conds] args
                                  col (db/col @db name)]
                              (for [result (query/q col conds)]
                                (vec (for [pos result]
                                       (col/by-pos col pos)))))))
          :fastselect (prn (vec (let [[name & poses] args
                                      col (db/col @db name)]
                                  (for [pos poses]
                                    (col/by-pos col pos)))))
          ; Index
          :hindex (do (col/index-path (db/col @db (first args)) (second args) :hash) (prn OK))
          :rindex (do (col/index-path (db/col @db (first args)) (second args) :range) (prn OK))
          :unindex (do (col/unindex-path (db/col @db (first args)) (second args)) (prn OK))
          :indexed (prn {:hash (vec (col/indexed (db/col @db (first args)) :hash)) :range (vec (col/indexed (db/col @db (first args)) :range))}))   
        (catch Exception e (prn {:err (.getMessage e)}))))))

(defn -main [& args]
  (if (> (count args) 1)
    (do
      (reset! db (db/open (nth args 1)))
      (doto ; Work on the request queue
        (Thread. #(loop [] (work (.take ^LinkedBlockingQueue Q)) (recur)))
        (.start))
      (doto ; Flush to disk every minute
        (Timer.)
        (.scheduleAtFixedRate
          (proxy [TimerTask] []
            (run [] (.offer ^LinkedBlockingQueue Q [*out* :save])))
          0 60000))
      (socket/server (Integer/parseInt (first args))
                     (fn [in out]
                       (binding [*in* (BufferedReader. (InputStreamReader. in))
                                 *out* (PrintWriter. ^OutputStream out)]
                         (loop []
                           (when-let [line (.readLine ^BufferedReader *in*)]
                             (try
                               (.offer ^LinkedBlockingQueue Q (cons *out* (read-string line)))
                               (catch Exception e (prn {:err (.getMessage e)})))
                             (recur)))))))
    (print "Usage : lein run [port_number] [database_directory]")))
