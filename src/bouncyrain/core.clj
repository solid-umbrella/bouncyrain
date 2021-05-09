(ns bouncyrain.core
  (:gen-class)
  (:require
   [aleph.http :as http]
   [compojure.core :as compojure :refer [ANY GET]]
   [ring.middleware.params :as params]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [manifold.bus :as bus]))

(def hooks (bus/event-bus))

(defn hook-handler
  [id]
  (fn [req]
    (bus/publish! hooks id (str req))
    {:status 200
     :headers {"content-type" "text/plain"}
     :body "OK"}))

(defn listen-handler
  [id]
  (fn [req]
    (d/let-flow [conn (d/catch
                       (http/websocket-connection req)
                       (fn [_] nil))]
                (if-not conn
                  {
                   :status 400
                   :headers {"content-type" "text/plain"}
                   :body "Expected a websocket request"
                  }
                  (s/connect
                   (bus/subscribe hooks id)
                   conn)))))

(def handle
  (params/wrap-params
   (compojure/routes
    (ANY "/h/:id" [id] (hook-handler id))
    (GET "/l/:id" [id] (listen-handler id)))))

(defn -main
  "Starts the server."
  [& _args]
  (let [port (or (some-> (System/getenv "PORT") Integer/parseInt) 1337)]
    (http/start-server handle {:port port})
    (println "Listening at port" port)))
