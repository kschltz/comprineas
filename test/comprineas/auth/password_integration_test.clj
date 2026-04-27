(ns comprineas.auth.password-integration-test
  "End-to-end password authentication tests — PRD-0002.
   Mirrors acceptance criteria from the PRD.
   Covers: AC-1 (registration), AC-2 (duplicate email), AC-3 (password login),
           AC-4 (anti-enumeration), AC-7 (forgot-password), AC-9 (reset flow),
           AC-11 (set-password for magic-link user)."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [comprineas.test-utils :as tu]
            [comprineas.auth.users :as users]
            [comprineas.auth.sessions :as sessions]
            [comprineas.auth.password :as password]
            [comprineas.auth.reset-tokens :as reset-tokens]
            [comprineas.auth.tokens :as tokens]
            [comprineas.auth.rate-limit :as rl]))

(use-fixtures :each tu/fixture-db)

(def secret "test-hmac-secret-32-bytes-long!!")

;; AC-1: Registration creates user with bcrypt hash + session
(deftest ac1-registration-flow
  (testing "AC-1: Register with email, password, display name"
    (let [ds   (tu/fresh-db)
          pw   "securepass123"
          hash (password/hash-password pw)
          user (users/create-user-with-password! ds "alice@example.com" "Alice" hash)]
      (is (some? user) "User created")
      (is (= "alice@example.com" (:email user)))
      (is (= "Alice" (:display_name user)))
      ;; Verify password is bcrypt-hashed and verifiable
      (let [full-user (users/find-by-email-with-password ds "alice@example.com")]
        (is (true? (password/verify-password pw (:password_hash full-user)))))
      ;; Can create a session for the new user
      (let [session (sessions/create-session! ds (:id user))]
        (is (some? session) "Session created for registered user")))))

;; AC-2: Duplicate email rejected
(deftest ac2-duplicate-email-rejected
  (testing "AC-2: Registration with existing email fails"
    (let [ds (tu/fresh-db)]
      (users/create-user-with-password! ds "bob@example.com" "Bob" (password/hash-password "password1"))
      ;; Second registration with same email should throw or conflict
      (is (some? (users/find-by-email ds "bob@example.com"))
          "User already exists")
      (is (thrown? Exception (users/create-user-with-password! ds "bob@example.com" "Bob2" (password/hash-password "password2")))
          "Duplicate email should cause unique constraint violation"))))

;; AC-3: Password login creates session
(deftest ac3-password-login-success
  (testing "AC-3: Correct email + password creates session"
    (let [ds   (tu/fresh-db)
          pw   "mypassword99"
          hash (password/hash-password pw)
          user (users/create-user-with-password! ds "carol@example.com" "Carol" hash)
          found (users/find-by-email-with-password ds "carol@example.com")]
      (is (some? found))
      (is (true? (password/verify-password pw (:password_hash found)))
          "Correct password verifies")
      (let [session (sessions/create-session! ds (:id user))]
        (is (some? session) "Session created on successful login")))))

;; AC-4 & AC-5: Anti-enumeration — same error whether email missing or pw wrong
(deftest ac4-ac5-anti-enumeration
  (testing "AC-4/AC-5: Same result for non-existent email vs wrong password"
    (let [ds (tu/fresh-db)
          ;; Register a user
          _   (users/create-user-with-password! ds "dave@example.com" "Dave" (password/hash-password "rightpass"))
          ;; Case 1: User does not exist
          missing (users/find-by-email-with-password ds "nobody@example.com")
          ;; Case 2: User exists but password is wrong
          found   (users/find-by-email-with-password ds "dave@example.com")]
      (is (nil? missing) "Non-existent email returns nil")
      (is (false? (password/verify-password "wrongpass" (:password_hash found)))
          "Wrong password returns false")
      ;; nil hash should also return nil (not throw) for safe anti-enumeration
      (is (false? (password/verify-password "anypass" nil))
          "nil hash returns false safely"))))

;; AC-7: Forgot-password creates reset token
(deftest ac7-forgot-password-token-creation
  (testing "AC-7: Password reset token is created and can be verified"
    (let [ds    (tu/fresh-db)
          email "eve@example.com"
          signed (reset-tokens/create-reset-token! ds email secret)
          {:keys [token]} (tokens/unsign-payload signed secret)]
      (is (some? signed) "Reset token was created")
      (is (some? token) "Signed payload contains token")
      (let [hash     (tokens/hash-token token)
            db-token (reset-tokens/find-valid-token ds hash)]
        (is (some? db-token) "Token is valid in DB")
        (is (= email (:email db-token)))))))

;; AC-9: Valid reset token allows password update
(deftest ac9-reset-password-flow
  (testing "AC-9: Valid reset token updates password"
    (let [ds    (tu/fresh-db)
          email "frank@example.com"
          ;; Create user (via magic link, no password)
          user  (users/ensure-user! ds email)
          ;; Create reset token
          signed (reset-tokens/create-reset-token! ds email secret)
          {:keys [token]} (tokens/unsign-payload signed secret)
          hash     (tokens/hash-token token)
          db-token (reset-tokens/find-valid-token ds hash)]
      (is (some? db-token))
      ;; Mark token as used
      (reset-tokens/mark-token-used! ds (:id db-token))
      ;; Token should no longer be valid
      (is (nil? (reset-tokens/find-valid-token ds hash))
          "Used token is no longer valid")
      ;; Set new password
      (let [new-pw   "newpassword123"
            new-hash (password/hash-password new-pw)
            _        (users/set-password-hash! ds email new-hash)
            refreshed (users/find-by-email-with-password ds email)]
        (is (true? (password/verify-password new-pw (:password_hash refreshed)))
            "New password verifies after reset")))))

;; AC-10: Expired/used token rejected
(deftest ac10-expired-used-token-rejected
  (testing "AC-10: Already-used reset token is rejected"
    (let [ds    (tu/fresh-db)
          email "grace@example.com"
          signed (reset-tokens/create-reset-token! ds email secret)
          {:keys [token]} (tokens/unsign-payload signed secret)
          hash     (tokens/hash-token token)
          db-token (reset-tokens/find-valid-token ds hash)]
      ;; Use the token
      (reset-tokens/mark-token-used! ds (:id db-token))
      ;; Second use should fail
      (is (nil? (reset-tokens/find-valid-token ds hash))
          "Already-used token is not valid"))))

;; AC-11: Set password for magic-link-only user
(deftest ac11-set-password-for-magic-link-user
  (testing "AC-11: Magic-link-only user can set a password"
    (let [ds (tu/fresh-db)
          ;; Create user via magic link (no password)
          user (users/ensure-user! ds "henry@example.com")
          found-before (users/find-by-email-with-password ds "henry@example.com")]
      (is (nil? (:password_hash found-before))
          "New magic-link user has no password hash")
      ;; Set password
      (let [pw   "first-password-1"
            hash (password/hash-password pw)]
        (users/set-password-hash! ds "henry@example.com" hash)
        (let [found-after (users/find-by-email-with-password ds "henry@example.com")]
          (is (some? (:password_hash found-after))
              "Password hash is now set")
          (is (true? (password/verify-password pw (:password_hash found-after)))
              "Set password verifies correctly"))))))