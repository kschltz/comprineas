(ns comprineas.auth.reset-tokens
  "Password reset token management.
   Reuses token utilities from comprineas.auth.tokens (random-token, hash-token, sign-payload).
   Per PRD-0002: HMAC-SHA256 signed tokens, 30-minute expiry, single-use."
  (:require [comprineas.db.core :as db]
            [comprineas.auth.tokens :as tokens]))

(def ^:private reset-expiry-minutes 30)

(defn create-reset-token!
  "Generate a password reset token for the given email.
   Creates a random token, stores its SHA-256 hash in the database,
   and returns an HMAC-SHA256 signed JWT payload containing email + token.
   The caller is responsible for sending the signed token via email."
  [ds email secret]
  (let [raw    (tokens/random-token)
        hashed (tokens/hash-token raw)]
    (db/execute-one! ds
                     [(str "INSERT INTO password_reset_tokens (email, token_hash, expires_at)"
                           " VALUES (?, ?, now() + INTERVAL '" reset-expiry-minutes " minutes')")
                      email hashed])
    (tokens/sign-payload {:email email :token raw :iat (quot (System/currentTimeMillis) 1000)} secret)))

(defn find-valid-token
  "Look up a password reset token by its SHA-256 hash.
   Returns the token row if found, not expired, and not yet used. Returns nil otherwise."
  [ds token-hash]
  (db/execute-one! ds
                   ["SELECT id, email, token_hash, expires_at, used_at, created_at
      FROM password_reset_tokens
      WHERE token_hash = ?
        AND expires_at > now()
        AND used_at IS NULL"
                    token-hash]))

(defn mark-token-used!
  "Mark a password reset token as used by setting used_at to now().
   Per PRD-0002 AC-10: tokens are single-use."
  [ds id]
  (db/execute-one! ds
                   ["UPDATE password_reset_tokens SET used_at = now() WHERE id = ?" id]))