(ns comprineas.auth.login-rate-limit-test
  "Tests for login rate limiting — PRD-0002.
   Covers: login-attempt-count, within-login-limit?, record-login-attempt!
   Corresponds to AC-6 (FR-8: 5 per IP per minute)."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [comprineas.test-utils :as tu]
            [comprineas.auth.rate-limit :as rl]))

(use-fixtures :each tu/fixture-db)

(deftest test-login-rate-limit-allows-under-max
  (testing "within-login-limit? returns true for <5 attempts"
    (let [ds (tu/fresh-db)]
      (dotimes [_ 4]
        (rl/record-login-attempt! ds "192.168.1.1"))
      (is (true? (rl/within-login-limit? ds "192.168.1.1"))
          "4 attempts should still be within limit")
      (is (= 4 (rl/login-attempt-count ds "192.168.1.1"))))))

(deftest test-login-rate-limit-blocks-at-max
  (testing "within-login-limit? returns false at ≥5 attempts"
    (let [ds (tu/fresh-db)]
      (dotimes [_ 5]
        (rl/record-login-attempt! ds "10.0.0.1"))
      (is (false? (rl/within-login-limit? ds "10.0.0.1"))
          "5 attempts should exceed limit (AC-6)")
      (is (= 5 (rl/login-attempt-count ds "10.0.0.1"))))))

(deftest test-login-rate-limit-per-ip-isolated
  (testing "rate limits are independent per IP"
    (let [ds (tu/fresh-db)]
      (dotimes [_ 5]
        (rl/record-login-attempt! ds "1.1.1.1"))
      (is (false? (rl/within-login-limit? ds "1.1.1.1")))
      (is (true? (rl/within-login-limit? ds "2.2.2.2"))
          "Different IP should have its own limit"))))