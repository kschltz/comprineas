(ns comprineas.auth.password-test
  "Tests for password hashing and validation — PRD-0002.
   Covers: hash-password, verify-password, valid-password?
   Corresponds to AC-1 (bcrypt), NFR-1 (cost ≥10), FR-1 (≥8 chars)."
  (:require [clojure.test :refer [deftest is testing]]
            [comprineas.auth.password :as password]))

(deftest test-valid-password
  (testing "valid-password? accepts passwords ≥8 characters"
    (is (true? (password/valid-password? "12345678")))
    (is (true? (password/valid-password? "a longer password")))
    (is (true? (password/valid-password? "password with spaces  ok"))))

  (testing "valid-password? rejects passwords <8 characters"
    (is (false? (password/valid-password? "1234567")))
    (is (false? (password/valid-password? "abc")))
    (is (false? (password/valid-password? ""))))

  (testing "valid-password? rejects nil and non-strings"
    (is (false? (password/valid-password? nil)))
    (is (false? (password/valid-password? 12345678)))))

(deftest test-hash-and-verify
  (testing "hash-password produces a verifiable bcrypt hash"
    (let [plain "test-password-123"
          hash  (password/hash-password plain)]
      (is (string? hash))
      (is (true? (password/verify-password plain hash)))
      ;; Wrong password fails
      (is (false? (password/verify-password "wrong-password" hash)))))

  (testing "verify-password returns false for nil hash (anti-enumeration safe)"
    (is (false? (password/verify-password "any-password" nil))))

  (testing "different passwords produce different hashes"
    (let [h1 (password/hash-password "password-A")
          h2 (password/hash-password "password-B")]
      (is (not= h1 h2))))

  (testing "same password produces different hashes (bcrypt salt)"
    ;; bcrypt generates a random salt each time
    (let [h1 (password/hash-password "same-password")
          h2 (password/hash-password "same-password")]
      (is (not= h1 h2))
      (is (true? (password/verify-password "same-password" h1)))
      (is (true? (password/verify-password "same-password" h2))))))