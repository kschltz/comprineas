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
  (let [base (routes/app {:db ds
                          :secrets {:hmac "e2e-hmac-secret-32-bytes!!"
                                    :session "e2e-session-secret-32-byt!"}
                          :mailer {:stub? true}})]
    (fn [req]
      (try
        (base req)
        (catch Exception e
          (.printStackTrace e)
          {:status 500
           :headers {"Content-Type" "text/plain"}
           :body (str "ERROR: " (.getMessage e))})))))

(defn -main [& _args]
  (let [{:keys [pg ds]} (start-embedded-pg!)
        port  3001
        handler (routes/app {:db ds
                             :secrets {:hmac "e2e-hmac-secret-32-bytes!!"
                                       :session "e2e-session-secret-32-byt!"}
                             :mailer {:stub? true}})]
    (jetty/run-jetty handler {:port port :join? false})
    (println (str "E2E_READY port=" port))
    (flush)
    (deref (promise))))
