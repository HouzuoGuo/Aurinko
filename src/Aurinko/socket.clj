; Modified from clojure.contrib.server-socket by Craig McDaniel
(ns Aurinko.socket
  (:import (java.net InetAddress ServerSocket Socket SocketException)
           (java.io InputStreamReader OutputStream OutputStreamWriter PrintWriter)
           (clojure.lang LineNumberingPushbackReader)))
(defn- on-thread [f]
  (doto (Thread. #^Runnable f)
    (.start)))

(defn- close-socket [#^Socket s]
  (when-not (.isClosed s)
    (doto s
      (.shutdownInput)
      (.shutdownOutput)
      (.close))))

(defn- accept-fn [#^Socket s connections fun]
  (let [ins (.getInputStream s)
        outs (.getOutputStream s)]
    (on-thread #(do
                  (dosync (commute connections conj s))
                  (try
                    (fun ins outs)
                    (catch SocketException e))
                  (close-socket s)
                  (dosync (commute connections disj s))))))

(defstruct server-def :server-socket :connections)

(defn server [port fun]
  (let [ss (ServerSocket. port)
        connections (ref #{})]
    (on-thread #(when-not (.isClosed ss)
                  (try
                    (accept-fn (.accept ss) connections fun)
                    (catch SocketException e))
                  (recur)))
    (struct-map server-def :server-socket ss :connections connections)))

(defn close-server [server]
  (doseq [s @(:connections server)]
    (close-socket s))
  (dosync (ref-set (:connections server) #{}))
  (.close #^ServerSocket (:server-socket server)))
