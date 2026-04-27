(ns comprineas.auth.rate-limit-test
  "Tests for rate limit enforcement.

  Directly mirrors actions from the Quint specification:
    docs/specs/0001-magic-link-auth.qnt

  Quint action covered:
    - requestMagicLink        => within-limit? / request-count
    - requestMagicLinkBlocked => rate limit exceeded

  Quint invariant covered:
    - invRateLimit => requestCountInWindow <= RATE_LIMIT_MAX"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [comprineas.test-utils :as tu]
            [comprineas.db.core :as db]
            [comprineas.auth.tokens :as tokens]
            [comprineas.auth.rate-limit :as rl]))

(use-fixtures :each tu/fixture-db)

(def secret "test-hmac-secret-32-bytes-long!!")

(deftest test-rate-limit-allows-under-max
  "Mirrors Quint: requestMagicLink succeeds while within limit.
  Rate limit window = 15 minutes, max = 3 requests."
  (let [ds (tu/fresh-db)]
    (is (rl/within-limit? ds "alice@test.com"))

    ;; First request: count=1 < 3
    (tokens/create-magic-token! ds "alice@test.com" secret)
    (is (rl/within-limit? ds "alice@test.com"))

    ;; Second request: count=2 < 3
    (tokens/create-magic-token! ds "alice@test.com" secret)
    (is (rl/within-limit? ds "alice@test.com"))

    ;; Third request: count=3 == max — still equal? No, < checks count < 3.
    ;; After 3rd creation count is 3; within-limit? returns false.
    (tokens/create-magic-token! ds "alice@test.com" secret)
    (is (not (rl/within-limit? ds "alice@test.com")))

    ;; invRateLimit: requestCountInWindow <= RATE_LIMIT_MAX (3)
    (is (= 3 (rl/request-count ds "alice@test.com")))))

(deftest test-rate-limit-blocks-at-max
  "Mirrors Quint: requestMagicLinkBlocked when limit reached.
  At max (3), the next request should be blocked."
  (let [ds (tu/fresh-db)]
    (dotimes [_ 3]
      (tokens/create-magic-token! ds "bob@test.com" secret))

    ;; Now at 3 requests — should still be in limit? Wait.
    ;; within-limit? checks < max (3), so at 3 it returns FALSE.
    (is (not (rl/within-limit? ds "bob@test.com")))

    ;; invRateLimit: requestCountInWindow <= RATE_LIMIT_MAX (3)
    (is (= 3 (rl/request-count ds "bob@test.com")))))

(deftest test-rate-limit-per-email-isolated
  "Mirrors Quint: rate limit is scoped per email address."
  (let [ds (tu/fresh-db)]
    (dotimes [_ 3]
      (tokens/create-magic-token! ds "charlie@test.com" secret))

    ;; Charlie is at limit
    (is (not (rl/within-limit? ds "charlie@test.com")))

    ;; Dave is unaffected
    (is (rl/within-limit? ds "dave@test.com"))))

(deftest test-rate-limit-resets-after-window
  "Mirrors Quint tick advancing time: tokens outside the 15-minute window
  no longer count toward the limit."
  ;; Direct time manipulation is difficult in embedded postgres;
  ;; we verify the SQL filter (created_at > now() - interval '15 min')
  ;; by looking at the query rather than sleeping.  The Quint model
  ;; (tick action) verifies this formally."
  (let [ds (tu/fresh-db)]
    ;; Create a token with created_at in the past
    (db/execute! ds ["INSERT INTO magic_link_tokens (email, token_hash, expires_at, created_at)
                        VALUES (?, ?, now() + INTERVAL '1 hour', now() - INTERVAL '16 minutes')"
                       "eve@test.com"
                       "fakehash1234567890abcdef1234567890abcdef1234567890abcdef12345678"])

    ;; Old token is outside the 15-minute window, so count is 0
    (is (rl/within-limit? ds "eve@test.com"))
    (is (= 0 (rl/request-count ds "eve@test.com")))))
