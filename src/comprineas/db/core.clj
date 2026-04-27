(ns comprineas.db.core
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [integrant.core :as ig]))

(defmethod ig/init-key :db/postgres [_ {:keys [jdbcUrl]}]
  (jdbc/with-options
    (jdbc/get-datasource {:jdbcUrl jdbcUrl})
    {:return-keys true
     :builder-fn rs/as-unqualified-lower-maps}))

(defmethod ig/halt-key! :db/postgres [_ _]
  nil)

(defn execute! [ds sql-params]
  (jdbc/execute! ds sql-params
    {:return-keys true
     :builder-fn rs/as-unqualified-lower-maps}))

(defn execute-one! [ds sql-params]
  (jdbc/execute-one! ds sql-params
    {:return-keys true
     :builder-fn rs/as-unqualified-lower-maps}))
