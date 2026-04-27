(ns comprineas.auth.cleanup-test
  "Tests for token/login cleanup — PRD-0002 NFR-6."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [comprineas.test-utils :as tu]
            [comprineas.auth.cleanup :as cleanup]
            [comprineas.auth.reset-tokens :as reset-tokens]
            [comprineas.auth.tokens :as tokens]
            [comprineas.auth.rate-limit :as rl]
            [comprineas.db.core :as db]))

(use-fixtures :each tu/fixture-db)

(def secret "test-hmac-secret-32-bytes-long!!")

(deftest test-purge-expired-reset-tokens
  (testing "purge-expired-reset-tokens! removes expired tokens"
    (let [ds (tu/fresh-db)]
      ;; Create a token (it has 30-min expiry, so it won't be expired yet)
      (reset-tokens/create-reset-token! ds "purge-test@example.com" secret)
      ;; Count before purge
      (let [before (db/execute-one! ds ["SELECT COUNT(*) AS count FROM password_reset_tokens"])]
        (is (= 1 (:count before))))
      ;; Purge should not remove non-expired tokens
      (cleanup/purge-expired-reset-tokens! ds)
      (let [after (db/execute-one! ds ["SELECT COUNT(*) AS count FROM password_reset_tokens"])]
        (is (= 1 (:count after)))))))

(deftest test-purge-old-login-attempts
  (testing "purge-old-login-attempts! removes stale records"
    (let [ds (tu/fresh-db)]
      ;; Record some attempts
      (rl/record-login-attempt! ds "1.2.3.4")
      (rl/record-login-attempt! ds "1.2.3.4")
      ;; Purge should not remove recent records
      (cleanup/purge-old-login-attempts! ds)
      (let [after (db/execute-one! ds ["SELECT COUNT(*) AS count FROM login_attempts"])]
        (is (= 2 (:count after)))))))