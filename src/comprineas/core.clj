(ns comprineas.core
  (:require [integrant.core :as ig]
            [comprineas.config :as config]
            ;; Require namespaces that define ig/init-key methods
            [comprineas.db.core]
            [comprineas.auth.mailer]
            [comprineas.web.server])
  (:gen-class))

(defn system-config []
  (let [cfg (config/read-config)]
    {:db/postgres     (:db cfg)
     :auth/mailer     (:email cfg)
     :server/http     {:db      (ig/ref :db/postgres)
                       :mailer  (ig/ref :auth/mailer)
                       :secrets (:secrets cfg)
                       :port    (get-in cfg [:server :port])}}))

(defn -main [& _args]
  (let [sys (ig/init (system-config))]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(ig/halt! sys)))
    ;; Block main thread
    @(promise)))
