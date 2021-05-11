(ns bouncyrain.core
  (:gen-class)
  (:require
   [aleph.http :as http]
   [clojure.string :as string]
   [compojure.core :as compojure :refer [ANY GET]]
   [ring.util.request :as request]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [manifold.bus :as bus]
   [clojure.data.json :as json]))

(def hooks (bus/event-bus))

(defn convert-to-json
  [req]
  (json/write-str {:ip (req :remote-addr)
                   :headers (req :headers)
                   :path (req :uri)
                   :query (req :query-string)
                   :host (req :server-name)
                   :port (req :server-port)
                   :scheme (req :scheme)
                   :method (string/upper-case (name (req :request-method)))
                   :body (request/body-string req)}
                  :escape-slash false))

(defn hook-handler
  [id]
  (fn [req]
    (bus/publish! hooks id (convert-to-json req))
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
                   :headers {"content-type" "application/json"}
                   :body req
                  }
                  (s/connect
                   (bus/subscribe hooks id)
                   conn)))))

(def handle
  (compojure/routes
   (ANY "/h/:id" [id] (hook-handler id))
   (GET "/v1/listen/:id" [id] (listen-handler id))))

(defn -main
  "Starts the server."
  [& _args]
  (let [port (or (some-> (System/getenv "PORT") Integer/parseInt) 1337)]
    (http/start-server handle {:port port})
    (println "Listening at port" port)))
