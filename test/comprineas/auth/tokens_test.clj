(ns comprineas.auth.tokens-test
  "Tests for magic-link token lifecycle.

  Directly mirrors actions from the Quint specification:
    docs/specs/0001-magic-link-auth.qnt

  Quint actions covered:
    - requestMagicLink       => create-magic-token!
    - clickMagicLink         => unsign-payload / find-valid-token / mark-token-used!
    - invNoReplay            => used token cannot be reused
    - invUsedWithinLifetime  => tokens consumed within 15-minute window"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [comprineas.test-utils :as tu]
            [comprineas.db.core :as db]
            [comprineas.auth.tokens :as tokens]))

(use-fixtures :each tu/fixture-db)

(def secret "test-hmac-secret-32-bytes-long!!")

(deftest test-request-magic-link
  "Mirrors Quint action: requestMagicLink(email)"
  (let [ds (tu/fresh-db)]
    (let [signed (tokens/create-magic-token! ds "alice@test.com" secret)]
      (is (string? signed))
      (is (seq signed))

      ;; A valid token should unsign to the correct email
      (let [payload (tokens/unsign-payload signed secret)]
        (is (= "alice@test.com" (:email payload)))
        (is (string? (:token payload)))
        (is (number? (:iat payload))))

      ;; Wrong secret should fail
      (let [bad (tokens/unsign-payload signed "wrong-secret")]
        (is (nil? bad))))))

(deftest test-click-magic-link-valid
  "Mirrors Quint action: clickMagicLink(tokenId) with a valid token.
  After clicking, the token is marked used and cannot be reused (invNoReplay)."
  (let [ds     (tu/fresh-db)
        signed (tokens/create-magic-token! ds "alice@test.com" secret)
        {:keys [token]} (tokens/unsign-payload signed secret)
        hash   (tokens/hash-token token)]

    ;; Token exists and is valid before clicking
    (let [db-token (tokens/find-valid-token ds hash)]
      (is (some? db-token))
      (is (= "alice@test.com" (:email db-token)))
      (is (nil? (:used_at db-token))))

    ;; Consume token
    (let [db-token (tokens/find-valid-token ds hash)]
      (tokens/mark-token-used! ds (:id db-token)))

    ;; Token is no longer valid (used) — invNoReplay enforced
    (let [db-token-again (tokens/find-valid-token ds hash)]
      (is (nil? db-token-again)))))

(deftest test-click-expired-link
  "Mirrors Quint clickMagicLink with expired token (isTokenValid returns false).
  A token past its 15-minute lifetime is not found."
  ;; Note: Embedded Postgres + real time makes this hard to test directly.
  ;; We verify that find-valid-token correctly excludes expired rows by
  ;; checking the SQL predicate includes expires_at > now().
  ;; Time-travel tests are covered in the Quint model (invUsedWithinLifetime)."
  (let [ds     (tu/fresh-db)
        signed (tokens/create-magic-token! ds "bob@test.com" secret)
        {:keys [token]} (tokens/unsign-payload signed secret)
        hash   (tokens/hash-token token)]

    ;; Token is valid immediately after creation
    (is (some? (tokens/find-valid-token ds hash)))

    ;; Simulate expiry by updating expires_at into the past
    (db/execute! ds ["UPDATE magic_link_tokens SET expires_at = now() - INTERVAL '1 minute'"])

    ;; Token is no longer valid (expired) — invUsedWithinLifetime enforcement
    (is (nil? (tokens/find-valid-token ds hash)))))

(deftest test-token-hash-deterministic
  "Hashing the same raw token yields the same hash (used for DB lookup)."
  (let [raw "my-secret-token-123"]
    (is (= (tokens/hash-token raw)
           (tokens/hash-token raw)))))
