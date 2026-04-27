(ns comprineas.auth.sessions
  (:require [comprineas.db.core :as db]))

(defn create-session! [ds user-id]
  (db/execute-one! ds
    ["INSERT INTO sessions (user_id, expires_at)
      VALUES (?, now() + INTERVAL '24 hours')
      RETURNING id, user_id, token, created_at, expires_at"
     user-id]))

(defn find-by-token [ds token]
  (db/execute-one! ds
    ["SELECT id, user_id, token, created_at, expires_at
      FROM sessions
      WHERE token = ? AND expires_at > now()"
     token]))

(defn find-by-id [ds id]
  (db/execute-one! ds
    ["SELECT id, user_id, token, created_at, expires_at
      FROM sessions
      WHERE id = ? AND expires_at > now()"
     id]))

(defn destroy-session! [ds id]
  (db/execute-one! ds
    ["DELETE FROM sessions WHERE id = ?" id]))
