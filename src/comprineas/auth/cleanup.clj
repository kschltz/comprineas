(ns comprineas.auth.cleanup
  "Background cleanup for expired tokens and old login attempts.
   Per PRD-0002 NFR-6: purge expired password_reset_tokens and stale login_attempts daily."
  (:require [comprineas.db.core :as db]))

(defn purge-expired-reset-tokens!
  "Delete all expired and already-used password reset tokens.
   Should be called by a scheduled job at least daily (NFR-6)."
  [db]
  (db/execute-one! db
                   ["DELETE FROM password_reset_tokens WHERE expires_at < now()"]))

(defn purge-old-login-attempts!
  "Delete login attempt records older than 24 hours.
   Keeps recent records for rate-limit queries but prevents unbounded growth."
  [db]
  (db/execute-one! db
                   ["DELETE FROM login_attempts WHERE attempted_at < now() - INTERVAL '24 hours'"]))

(defn purge-expired-magic-tokens!
  "Delete expired magic link tokens (from PRD-0001).
   Included here for completeness since the same cleanup job should handle both."
  [db]
  (db/execute-one! db
                   ["DELETE FROM magic_link_tokens WHERE expires_at < now()"]))

(defn cleanup-all!
  "Run all cleanup operations. Call this from a scheduled job."
  [db]
  (purge-expired-reset-tokens! db)
  (purge-old-login-attempts! db)
  (purge-expired-magic-tokens! db))