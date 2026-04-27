(ns comprineas.test-utils
  "Test utilities for embedded PostgreSQL and schema setup.

  All auth tests use an embedded Postgres instance so CI and local runs
  do not require an external database server.

  Schema is created by running migrations directly against the embedded
  instance on first use."
  (:require [comprineas.db.core :as db]
            [comprineas.db.migrations :as migrations]
            [clojure.java.io :as io]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import [io.zonky.test.db.postgres.embedded EmbeddedPostgres]))

(def ^:private pg-atom (atom nil))

(defn- start-postgres!
  []
  (let [pg (.start (EmbeddedPostgres/builder))
        ds (.getPostgresDatabase pg)]
    (let [raw-ds (jdbc/with-options ds {:builder-fn rs/as-unqualified-lower-maps})]
      (migrations/create-migrations-table! raw-ds)
      (doseq [file (migrations/migration-files)]
        (migrations/run-migration! raw-ds file)))
    {:pg pg :ds ds}))

(defn datasource
  "Return a next.jdbc datasource pointing to the embedded Postgres.
  Initializes and migrates on first call."
  []
  (when (nil? @pg-atom)
    (reset! pg-atom (start-postgres!)))
  (:ds @pg-atom))

(defn wrap-ds
  "Wrap a bare datasource with unqualified-lower-maps builder, like db.core does."
  [ds]
  (jdbc/with-options ds {:builder-fn rs/as-unqualified-lower-maps}))

(defn fresh-db
  "Return a freshly-migrated datasource wrapper (unqualified lower maps)."
  []
  (wrap-ds (datasource)))

(defn clear-auth-tables!
  "Wipe all auth tables between tests so each test starts clean."
  [ds]
  (db/execute! ds ["TRUNCATE sessions, magic_link_tokens, password_reset_tokens, login_attempts, users, schema_migrations CASCADE"]))

(defn fixture-db
  "A clojure.test fixture that clears auth tables before each test."
  [test-fn]
  (clear-auth-tables! (fresh-db))
  (test-fn))
