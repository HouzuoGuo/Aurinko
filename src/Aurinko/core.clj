(ns Aurinko.core
  (:require (Aurinko [socket :as socket] [db :as db] [col :as col] [hash :as hash] [query :as query]))
  (:import (java.io BufferedReader InputStreamReader OutputStream PrintWriter))
  (:import (java.util.concurrent LinkedBlockingQueue))
  (:import (java.util Timer TimerTask)))

(def Q (LinkedBlockingQueue.)) ; all read/write operations are queued in here
(def db (atom nil))

(defn enQ [^PrintWriter p fun show-results]
  (.offer ^LinkedBlockingQueue Q
    #(binding [*out* p]
       (try
         (let [result (fun)]
           (prn (if show-results
                  {:result (vec result) :ok true}
                  {:ok true})))
         (catch Exception e (prn {:err (.getMessage e)}) (.printStackTrace e))))))

(def ^:const cmds
  {; database commands
   :create   (fn [p [name & _]]    (enQ p (fn [] (db/create   @db name)) false))
   :rename   (fn [p [old new & _]] (enQ p (fn [] (db/rename   @db old new)) false))
   :drop     (fn [p [name & _]]    (enQ p (fn [] (db/delete   @db name)) false))
   :compress (fn [p [name & _]]    (enQ p (fn [] (db/compress @db name)) false))
   :repair   (fn [p [name & _]]    (enQ p (fn [] (db/repair   @db name)) false))
   :all      (fn [p & _] (enQ p (fn [] (db/all @db)) true))
   :stop     (fn [p & _] (enQ p (fn [] (do (db/close @db) (System/exit 0))) false))
   ; table comamnds
   :hindex  (fn [p [name & path]]  (enQ p (fn [] (col/index-path   (db/col @db name) (vec path))) false))
   :unindex (fn [p [name & path]]  (enQ p (fn [] (col/unindex-path (db/col @db name) (vec path))) false))
   :indexed (fn [p [name & _]]     (enQ p (fn [] (col/indexed      (db/col @db name))) true))
   :insert  (fn [p [name doc & _]] (enQ p (fn [] (col/insert       (db/col @db name) doc)) false))
   :findall (fn [p [name & _]]     (enQ p (fn [] (col/all          (db/col @db name))) true))
   :q       (fn [p [name & conds]] (enQ p (fn [] (query/q          (db/col @db name) conds)) true))
   :select  (fn [p [name & conds]]
              (enQ p (fn [] (let [col (db/col @db name)]
                              (for [result (query/q (db/col @db name) conds)]
                                (vec (for [pos result]
                                       (col/by-pos col pos)))))) true))
   :delete  (fn [p [name & conds]]
              (enQ p (fn [] (let [col (db/col @db name)]
                              (doseq [result (query/q col conds)]
                                (doseq [pos result]
                                  (col/delete col {:_pos pos}))))) false))
   :update  (fn [p [name func & conds]]
              (enQ p (fn [] (let [col (db/col @db name)]
                              (doseq [result (query/q col conds)]
                                (doseq [pos result]
                                  (col/update col
                                              (func (col/by-pos col pos))))))) false))})

(defn -main [& args]
  (if (> (count args) 1)
    (do
      (reset! db (db/open (nth args 1)))
      (doto (Thread. #(loop [] ((.take ^LinkedBlockingQueue Q)) (recur)))
        (.start)) ; only one worker to work on the queue
      (doto (Timer.)
        (.scheduleAtFixedRate
          (proxy [TimerTask] []
            (run [] (enQ *out* #(db/save @db) false)))
          0 60000)) ; flush data to disk every 60 seconds
      (socket/server (Integer/parseInt (first args))
                     (fn [in out]
                       (binding [*in* (BufferedReader. (InputStreamReader. in))
                                 *out* (PrintWriter. ^OutputStream out)]
                         (loop []
                           (let [line (.readLine ^BufferedReader *in*)]
                             (when line
                               (try
                                 (let [red  (load-string line)
                                       cmd  ((first red) cmds)]
                                   (if cmd
                                     (cmd *out* (rest red))
                                     (throw (Exception. (str "Unknown command" cmd)))))
                                 (catch Exception e (prn {:err (.getMessage e)})))
                               (recur))))))))
    (prn "usage: lein run port_number db_directory")))
