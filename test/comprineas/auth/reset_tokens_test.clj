(ns comprineas.auth.reset-tokens-test
  "Tests for password reset tokens — PRD-0002.
   Covers: create-reset-token!, find-valid-token, mark-token-used!
   Corresponds to AC-7 (token creation), AC-10 (expiry/usage), NFR-2 (HMAC), NFR-3 (entropy)."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [comprineas.test-utils :as tu]
            [comprineas.auth.reset-tokens :as reset-tokens]
            [comprineas.auth.tokens :as tokens]))

(use-fixtures :each tu/fixture-db)

(def secret "test-hmac-secret-32-bytes-long!!")

(deftest test-create-and-find-reset-token
  (testing "create-reset-token! produces a verifiable signed payload"
    (let [ds     (tu/fresh-db)
          email  "reset-test@example.com"
          signed (reset-tokens/create-reset-token! ds email secret)
          {:keys [token]} (tokens/unsign-payload signed secret)]
      (is (some? signed) "Signed token is non-nil")
      (is (some? token) "Unsign payload contains token")
      (let [hash     (tokens/hash-token token)
            db-token (reset-tokens/find-valid-token ds hash)]
        (is (some? db-token) "Token found in DB")
        (is (= email (:email db-token)) "Token email matches")
        (is (nil? (:used_at db-token)) "Token is unused")))))

(deftest test-mark-token-used
  (testing "mark-token-used! invalidates a reset token"
    (let [ds     (tu/fresh-db)
          email  "used-test@example.com"
          signed (reset-tokens/create-reset-token! ds email secret)
          {:keys [token]} (tokens/unsign-payload signed secret)
          hash     (tokens/hash-token token)
          db-token (reset-tokens/find-valid-token ds hash)]
      (is (some? db-token))
      (reset-tokens/mark-token-used! ds (:id db-token))
      ;; After marking used, should not be findable
      (is (nil? (reset-tokens/find-valid-token ds hash))
          "Used token should not be valid (AC-10)"))))

(deftest test-tampered-token-rejected
  (testing "tampered HMAC payload is rejected"
    (let [ds (tu/fresh-db)]
      (is (nil? (tokens/unsign-payload "tampered.payload.signature" secret))
          "Tampered token should fail unsign"))))

(deftest test-reset-token-email-association
  (testing "token is associated with the correct email"
    (let [ds     (tu/fresh-db)
          email1 "alice@example.com"
          email2 "bob@example.com"
          s1     (reset-tokens/create-reset-token! ds email1 secret)
          s2     (reset-tokens/create-reset-token! ds email2 secret)
          t1     (:token (tokens/unsign-payload s1 secret))
          t2     (:token (tokens/unsign-payload s2 secret))]
      (let [db1 (reset-tokens/find-valid-token ds (tokens/hash-token t1))]
        (is (= email1 (:email db1))))
      (let [db2 (reset-tokens/find-valid-token ds (tokens/hash-token t2))]
        (is (= email2 (:email db2)))))))