(ns comprineas.e2e-server
  "Bootstraps the full app with embedded PostgreSQL for Playwright e2e tests.
   Prints port to stdout when ready, then blocks until stdin closes."
  (:require [comprineas.db.migrations :as migrations]
            [comprineas.web.routes :as routes]
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
  (let [handler (routes/app {:db ds
               :secrets {:hmac "e2e-hmac-secret-32-bytes!!"
                         :session "e2e-session-secret-32-byt!"}
               :mailer {:stub? true}})]
    ;; Wrap with error logging to catch dashboard crashes
    (fn [req]
      (try
        (handler req)
        (catch Exception e
          (println "[ERROR]" (.getMessage e))
          (.printStackTrace e)
          {:status 500 :headers {"Content-Type" "text/plain"} :body (str "Error: " (.getMessage e))}))))))

(defn -main [& _args]
  (let [{:keys [pg ds]} (start-embedded-pg!)
        port  3001
        jetty (jetty/run-jetty (app {:ds ds :port port})
                               {:port port :join? false})]
    (println (str "E2E_READY port=" port))
    (flush)
    ;; Block forever — Playwright teardown sends SIGTERM
    (deref (promise))))
