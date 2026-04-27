(ns comprineas.auth.users
  (:require [comprineas.db.core :as db]))

(defn find-by-email [ds email]
  (db/execute-one! ds
                   ["SELECT id, email, display_name, created_at, updated_at FROM users WHERE email = ?" email]))

(defn find-by-id [ds id]
  (db/execute-one! ds
                   ["SELECT id, email, display_name, created_at, updated_at FROM users WHERE id = ?" id]))

(defn find-by-email-with-password [ds email]
  (db/execute-one! ds
                   ["SELECT id, email, display_name, password_hash, created_at, updated_at FROM users WHERE email = ?" email]))

(defn find-by-id-with-password [ds id]
  (db/execute-one! ds
                   ["SELECT id, email, display_name, password_hash, created_at, updated_at FROM users WHERE id = ?" id]))

(defn create-user! [ds email]
  (db/execute-one! ds
                   ["INSERT INTO users (email) VALUES (?) RETURNING id, email, display_name" email]))

(defn create-user-with-password! [ds email display-name password-hash]
  (db/execute-one! ds
                   ["INSERT INTO users (email, display_name, password_hash) VALUES (?, ?, ?) RETURNING id, email, display_name"
                    email display-name password-hash]))

(defn set-display-name! [ds email name]
  (db/execute-one! ds
                   ["UPDATE users SET display_name = ?, updated_at = now() WHERE email = ? RETURNING id, email, display_name"
                    name email]))

(defn set-password-hash! [ds email password-hash]
  (db/execute-one! ds
                   ["UPDATE users SET password_hash = ?, updated_at = now() WHERE email = ? RETURNING id, email, display_name"
                    password-hash email]))

(defn ensure-user! [ds email]
  (or (find-by-email ds email)
      (create-user! ds email)))
