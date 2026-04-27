(ns comprineas.e2e-server
  "Bootstraps the full app with embedded PostgreSQL for Playwright e2e tests.
   Prints port to stdout when ready, then blocks until stdin closes."
  (:require [comprineas.db.migrations :as migrations]
            [comprineas.web.routes :as routes]
            [comprineas.web.server :as server]
            [comprineas.db.core :as db]
            [clojure.java.io :as io]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [ring.adapter.jetty :as jetty])
  (:import [io.zonky.test.db.postgres.embedded EmbeddedPostgres]))

(defn- start-embedded-pg! []
  (let [pg     (.start (EmbeddedPostgres/builder))
        raw-ds (.getPostgresDatabase pg)
        ds     (jdbc/with-options raw-ds {:builder-fn rs/as-unqualified-lower-maps})]
    (migrations/create-migrations-table! ds)
    (doseq [f (migrations/migration-files)]
      (migrations/run-migration! ds f))
    {:pg pg :ds ds}))

(defn- app [{:keys [ds port]}]
  (routes/app {:db ds
               :secrets {:hmac "e2e-hmac-secret-32-bytes!!"
                         :session "e2e-session-secret-32-byt!"}
               :mailer {:stub? true}}))

(defn -main [& _args]
  (let [{:keys [pg ds]} (start-embedded-pg!)
        port  3001
        jetty (jetty/run-jetty (app {:ds ds :port port})
                               {:port port :join? false})]
    (println (str "E2E_READY port=" port))
    (flush)
    ;; Block until stdin closes (Playwright teardown kills process)
    (try
      (while (pos? (.read System/in -1))
        (Thread/sleep 100))
      (catch Exception _))
    (.stop jetty)
    (.close pg)
    (shutdown-agents)))
