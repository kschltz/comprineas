(ns comprineas.auth.rate-limit
  "Rate limiting for authentication endpoints.
   - Magic link requests: per-email, 3 per 15-minute window.
   - Login attempts: per-IP, 5 per 1-minute window (per PRD-0002 FR-8)."
  (:require [comprineas.db.core :as db]))

;; Magic link rate limiting (per email)
(def ^:private request-window-minutes 15)
(def ^:private max-requests 3)

(defn request-count [ds email]
  (or (-> (db/execute-one! ds
                           [(str "SELECT COUNT(*) AS count"
                                 " FROM magic_link_tokens"
                                 " WHERE email = ?"
                                 "   AND created_at > now() - INTERVAL '" request-window-minutes " minutes'")
                            email])
          :count)
      0))

(defn within-limit? [ds email]
  (< (request-count ds email) max-requests))

;; Login rate limiting (per IP, per PRD-0002 FR-8)
(def ^:private login-window-seconds 60)
(def ^:private max-login-attempts 5)

(defn login-attempt-count
  "Count login attempts from a given IP address within the sliding window."
  [ds ip-address]
  (or (-> (db/execute-one! ds
                           [(str "SELECT COUNT(*) AS count"
                                 " FROM login_attempts"
                                 " WHERE ip_address = ?"
                                 "   AND attempted_at > now() - INTERVAL '" login-window-seconds " seconds'")
                            ip-address])
          :count)
      0))

(defn within-login-limit?
  "Check whether a given IP address is within the login rate limit (5 per minute)."
  [ds ip-address]
  (< (login-attempt-count ds ip-address) max-login-attempts))

(defn record-login-attempt!
  "Record a login attempt from the given IP address for rate-limit tracking."
  [ds ip-address]
  (db/execute-one! ds
                   ["INSERT INTO login_attempts (ip_address) VALUES (?)" ip-address]))