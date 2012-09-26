(ns Aurinko.core
  (:require (Aurinko [socket :as socket] [db :as db] [col :as col] [query :as query]))
  (:import (java.io BufferedReader InputStreamReader OutputStream PrintWriter))
  (:import (java.util.concurrent LinkedBlockingQueue))
  (:import (java.util Timer TimerTask)))

(def db (atom nil))
(def ^:const OK {:ok true})

(defn -main [& args]
  (if (> (count args) 1)
    (do
      (reset! db (db/open (nth args 1)))
      (doto ; Flush to disk every minute
        (Timer.)
        (.scheduleAtFixedRate
          (proxy [TimerTask] []
            (run [] (locking db (db/save @db))))
          0 60000))
      (socket/server (Integer/parseInt (first args))
                     (fn [in out]
                       (binding [*in* (BufferedReader. (InputStreamReader. in))
                                 *out* (PrintWriter. ^OutputStream out)]
                         (loop []
                           (when-let [line (.readLine ^BufferedReader *in*)]
                             (try
                               (let [[cmd & args] (read-string line)]
                                 (case cmd
                                   ; DB management
                                   :create   (do (locking db (db/create @db (first args))) (prn OK))
                                   :rename   (do (locking db (db/rename @db (first args) (second args))) (prn OK))
                                   :drop     (do (locking db (db/delete @db (first args))) (prn OK))
                                   :compress (do (locking db (db/compress @db (first args))) (prn OK))
                                   :repair   (do (locking db (db/repair @db (first args))) (prn OK))
                                   :all      (prn (vec (locking db (db/all @db))))
                                   :stop     (do (locking db (db/close @db)) (prn OK) (System/exit 0))
                                   ; Document management
                                   :findall (do (prn (let [all-docs (transient [])]
                                                       (locking db (col/all (db/col @db (first args)) #(conj! all-docs %)))
                                                       (persistent! all-docs))))
                                   :insert (do (locking db (col/insert (db/col @db (first args)) (second args))) (prn OK))
                                   :update (let [[name func & conds] args
                                                 evaled (eval func)]
                                             (locking db
                                               (let [col (db/col @db name)]
                                                 (doseq [result (query/q col conds)]
                                                   (doseq [pos result]
                                                     (col/update col (evaled (col/by-pos col pos)))))))
                                             (prn OK))
                                   :delete (do (locking db
                                                 (let [[name & conds] args
                                                       col (db/col @db name)]
                                                   (doseq [result (query/q col conds)]
                                                     (doseq [pos result]
                                                       (col/delete col {:_pos pos})))))
                                             (prn OK))
                                   :fastupdate (do (locking db
                                                     (let [[name & docs] args
                                                           col (db/col @db name)]
                                                       (doseq [doc docs] (col/update col doc))))
                                                 (prn OK))
                                   :fastdelete (do (locking db
                                                     (let [[name & poses] args
                                                           col (db/col @db name)]
                                                       (doseq [pos poses] (col/delete col {:_pos pos}))))
                                                 (prn OK))
                                   ; Query
                                   :q (let [[name & conds] args]
                                        (prn (vec (locking db (query/q (db/col @db (first args)) conds)))))
                                   :select (prn (vec (locking db
                                                       (let [[name & conds] args
                                                             col (db/col @db name)]
                                                         (for [result (query/q col conds)]
                                                           (vec (for [pos result]
                                                                  (col/by-pos col pos))))))))
                                   :fastselect (prn (vec (locking db
                                                           (let [[name & poses] args
                                                                 col (db/col @db name)]
                                                             (for [pos poses]
                                                               (col/by-pos col pos))))))
                                   ; Index
                                   :hindex (do (locking db (col/index-path (db/col @db (first args)) (second args))) (prn OK))
                                   :unindex (do (locking db (col/unindex-path (db/col @db (first args)) (second args))) (prn OK))
                                   :indexed (prn {:hash (vec (locking db (col/indexed (db/col @db (first args)))))})))
                               (catch Exception e (prn {:err (.getMessage e)})))
                             (recur)))))))
    (print "Usage : lein run [port_number] [database_directory]")))