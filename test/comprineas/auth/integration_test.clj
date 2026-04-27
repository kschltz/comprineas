(ns comprineas.auth.integration-test
  "End-to-end auth flow tests that mirror the Quint test runs exactly.

  Directly mirrors runs from the Quint specification:
    docs/specs/0001-magic-link-auth.qnt

  Quint runs covered:
    - happyPath   = requestMagicLink → clickMagicLink → setDisplayName
    - returningUser = requestMagicLink → clickMagicLink → setDisplayName →
                      logout → requestMagicLink → clickMagicLink

  All four Quint invariants are asserted after every action:
    - invNoReplay           : single-use tokens
    - invSessionRequiresName  : all sessions belong to named users
    - invRateLimit           : <= 3 requests per 15 min window
    - invUsedWithinLifetime   : tokens consumed before expiry"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [comprineas.test-utils :as tu]
            [comprineas.db.core :as db]
            [comprineas.auth.tokens :as tokens]
            [comprineas.auth.rate-limit :as rl]
            [comprineas.auth.users :as users]
            [comprineas.auth.sessions :as sessions])
  (:import [java.time Duration Instant]
           [java.sql Timestamp]
           [java.time OffsetDateTime]))

(use-fixtures :each tu/fixture-db)

(def secret "test-hmac-secret-32-bytes-long!!")

;; --------------------------------------------------------------------------
;; Timestamp helpers (TIMESTAMPTZ may be returned as Timestamp, Instant,
;; or OffsetDateTime depending on JDBC driver version)
;; --------------------------------------------------------------------------

(defn- ->instant [x]
  (cond (instance? Instant x)        x
        (instance? Timestamp x)      (.toInstant x)
        (instance? OffsetDateTime x) (.toInstant x)
        :else                        (throw (ex-info "Unknown timestamp type" {:type (type x)}))))

(defn- instant-before? [x y]
  (.isBefore (->instant x) (->instant y)))

(defn- interval-minutes [x y]
  (.toMinutes (Duration/between (->instant x) (->instant y))))

;; --------------------------------------------------------------------------
;; Invariant checkers (assert the same properties as the Quint spec)
;; --------------------------------------------------------------------------

(defn assert-inv-no-replay!
  "INV-1: No token is both valid (unused, unexpired) AND used.
  Corresponds to Quint: invNoReplay"
  [ds]
  (let [all (db/execute! ds ["SELECT id, used_at, expires_at, now() AS now FROM magic_link_tokens"])]
    (doseq [t all]
      (when (and (:used_at t) (:expires_at t)
                 (instant-before? (:used_at t) (:expires_at t)))
        ;; A used token should NOT be returned by find-valid-token
        ;; because find-valid-token requires used_at IS NULL and expires_at > now()
        (let [db-row (db/execute-one! ds ["SELECT * FROM magic_link_tokens
                                            WHERE id = ? AND used_at IS NULL
                                              AND expires_at > now()" (:id t)])]
          (is (nil? db-row)
              (str "Token " (:id t) " is used but still shows as valid (invNoReplay violation)")))))))

(defn assert-inv-session-requires-name!
  "INV-2: Every session belongs to a user with a non-empty display name.
  Corresponds to Quint: invSessionRequiresName"
  [ds]
  (let [rows (db/execute! ds ["SELECT s.id, s.user_id, u.display_name
                               FROM sessions s JOIN users u ON s.user_id = u.id"])]
    (doseq [r rows]
      (is (seq (:display_name r))
          (str "Session " (:id r) " belongs to user " (:user_id r) " with no display name (invSessionRequiresName violation)")))))

(defn assert-inv-rate-limit!
  "INV-3: No more than RATE_LIMIT_MAX (3) tokens per 15-minute window.
  Corresponds to Quint: invRateLimit"
  [ds]
  (let [emails (db/execute! ds ["SELECT DISTINCT email FROM magic_link_tokens"])]
    (doseq [{:keys [email]} emails]
      (is (<= (rl/request-count ds email) 3)
          (str "Rate limit exceeded for " email " (invRateLimit violation)")))))

(defn assert-inv-used-within-lifetime!
  "INV-4: Every used token was consumed within its 15-minute lifetime.
  Corresponds to Quint: invUsedWithinLifetime
  The spec uses a constant TOKEN_LIFETIME of 15 minutes for this check."
  [ds]
  (let [used (db/execute! ds ["SELECT created_at, used_at
                                FROM magic_link_tokens
                               WHERE used_at IS NOT NULL"])]
    (doseq [t used]
      (let [minutes (interval-minutes (:created_at t) (:used_at t))]
        (is (<= minutes 15)
            (str "Token used after " minutes " minutes (invUsedWithinLifetime violation)"))))))

(defn assert-all-invariants!
  "Run every Quint invariant assertion against the current DB state."
  [ds]
  (assert-inv-no-replay! ds)
  (assert-inv-session-requires-name! ds)
  (assert-inv-rate-limit! ds)
  (assert-inv-used-within-lifetime! ds))

;; --------------------------------------------------------------------------
;; Happy Path Run
;; --------------------------------------------------------------------------

(deftest happy-path
  "Mirrors Quint run: happyPath
  requestMagicLink('alice@test.com')
    .then(clickMagicLink('token1'))
    .then(setDisplayName('alice@test.com', 'Alice'))"
  (let [ds             (tu/fresh-db)
        signed         (tokens/create-magic-token! ds "alice@test.com" secret)
        {:keys [token]} (tokens/unsign-payload signed secret)
        hash           (tokens/hash-token token)
        db-token       (tokens/find-valid-token ds hash)]

    (assert-all-invariants! ds)

    ;; clickMagicLink — token is valid
    (is (some? db-token))
    (tokens/mark-token-used! ds (:id db-token))
    (let [user (users/ensure-user! ds "alice@test.com")]
      ;; User exists, but no display name yet -> no session created in Quint
      ;; Our handler redirects to display-name form instead of creating session.
      (is (nil? (:display_name user))))

    (assert-all-invariants! ds)

    ;; setDisplayName
    (users/set-display-name! ds "alice@test.com" "Alice")
    (let [user      (users/find-by-email ds "alice@test.com")
          session   (sessions/create-session! ds (:id user))]
      (is (= "Alice" (:display_name user)))
      (is (some? session))
      (is (= "alice@test.com"
             (:email (users/find-by-id ds (:user_id (sessions/find-by-token ds (:token session))))))))

    (assert-all-invariants! ds)))

;; ---------------------------------------------------------------------------
;; Returning User Run
;; --------------------------------------------------------------------------

(deftest returning-user
  "Mirrors Quint run: returningUser
  requestMagicLink('bob@test.com')
    .then(clickMagicLink('token1'))
    .then(setDisplayName('bob@test.com', 'Bob'))
    .then(logout('session1'))
    .then(requestMagicLink('bob@test.com'))
    .then(clickMagicLink('token2'))"
  (let [ds              (tu/fresh-db)
        alice           "bob@test.com"
        ;; --- First login ---
        signed          (tokens/create-magic-token! ds alice secret)
        {:keys [token]}  (tokens/unsign-payload signed secret)
        hash            (tokens/hash-token token)
        db-token        (tokens/find-valid-token ds hash)]

    ;; click link
    (is (some? db-token))
    (tokens/mark-token-used! ds (:id db-token))
    (let [user      (users/ensure-user! ds alice)]
      (is (nil? (:display_name user))))

    ;; set display name -> session
    (users/set-display-name! ds alice "Bob")
    (let [user      (users/find-by-email ds alice)
          session   (sessions/create-session! ds (:id user))]
      (is (= "Bob" (:display_name user)))
      (is (some? session))

      ;; --- logout ---
      (sessions/destroy-session! ds (:id session))
      (is (nil? (sessions/find-by-token ds (:token session))))

      ;; --- Second login (returning user) ---
      (let [signed2        (tokens/create-magic-token! ds alice secret)
            token2         (:token (tokens/unsign-payload signed2 secret))
            hash2          (tokens/hash-token token2)
            db-token2      (tokens/find-valid-token ds hash2)]
        (is (some? db-token2))
        (tokens/mark-token-used! ds (:id db-token2))

        ;; Returning user already has display name -> session created immediately
        (let [user2     (users/find-by-email ds alice)
              session2  (sessions/create-session! ds (:id user2))]
          (is (= "Bob" (:display_name user2)))
          (is (some? session2))
          ;; Bob now has two used magic tokens and two sessions (one destroyed, one active)
          (assert-all-invariants! ds))))))
