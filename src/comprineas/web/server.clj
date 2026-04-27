(ns comprineas.web.server
  (:require [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [comprineas.web.routes :as routes]))

(defmethod ig/init-key :server/http [_ {:keys [db secrets port]}]
  (let [handler (routes/app {:db db :secrets secrets})]
    (println (str "Starting server on port " port))
    (jetty/run-jetty handler {:port port :join? false})))

(defmethod ig/halt-key! :server/http [_ server]
  (.stop server))
