(ns comprineas.db.migrations
  (:require [comprineas.config :as config]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:gen-class))

(defn- datasource [cfg]
  (jdbc/with-options
    (jdbc/get-datasource (:db cfg))
    {:builder-fn rs/as-unqualified-lower-maps}))

(defn create-migrations-table! [ds]
  (jdbc/execute! ds
    ["CREATE TABLE IF NOT EXISTS schema_migrations (
       id VARCHAR(255) PRIMARY KEY,
       applied_at TIMESTAMPTZ DEFAULT now()
     )"]))

(defn applied-migrations [ds]
  (try
    (->> (jdbc/execute! ds ["SELECT id FROM schema_migrations"])
         (map :id)
         (set))
    (catch Exception _ #{})))

(defn migration-files []
  (->> (io/file "resources/migrations")
       (.listFiles)
       (seq)
       (filter #(.endsWith (.getName %) ".sql"))
       (sort-by #(.getName %))))

(defn split-sql [sql]
  (->> (str/split sql #";\s*\n")
       (map str/trim)
       (remove str/blank?)
       (map #(str/replace % #";\s*$" ""))))

(defn run-migration! [ds file]
  (let [sql   (slurp file)
        id    (.getName file)
        stmts (split-sql sql)]
    (println "Running migration:" id (str "(" (count stmts) " statements)"))
    (doseq [stmt stmts]
      (jdbc/execute! ds [stmt]))
    (jdbc/execute! ds ["INSERT INTO schema_migrations (id) VALUES (?) ON CONFLICT DO NOTHING" id])
    (println "Done:" id)))

(defn -main [& _args]
  (let [cfg (config/read-config)
        ds  (datasource cfg)]
    (create-migrations-table! ds)
    (let [applied (applied-migrations ds)
          files   (migration-files)]
      (doseq [file files]
        (let [id (.getName file)]
          (if (applied id)
            (println "Skipping (already applied):" id)
            (run-migration! ds file))))
      (println "All migrations complete."))))
