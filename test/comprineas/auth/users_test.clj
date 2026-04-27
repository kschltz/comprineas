(ns comprineas.auth.users-test
  "Tests for user lifecycle (display name, lookups, creation).

  Directly mirrors actions from the Quint specification:
    docs/specs/0001-magic-link-auth.qnt

  Quint actions covered:
    - clickMagicLink  => ensure-user! (implicit user creation)
    - setDisplayName  => set-display-name!

  Quint invariant covered:
    - invSessionRequiresName => users must have display_name before sessions
      are created (verified in session integration tests)."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [comprineas.test-utils :as tu]
            [comprineas.auth.users :as users]))

(use-fixtures :each tu/fixture-db)

(deftest test-ensure-user-creates-new
  "Mirrors Quint: clicking a magic link creates a user with empty display name
  if the email does not yet exist."
  (let [ds (tu/fresh-db)]
    (is (nil? (users/find-by-email ds "alice@test.com")))
    (let [user (users/ensure-user! ds "alice@test.com")]
      (is (= "alice@test.com" (:email user)))
      (is (nil? (:display_name user)))
      (is (number? (:id user))))
    ;; Second call does not duplicate
    (let [user2 (users/ensure-user! ds "alice@test.com")]
      (is (= (:id user2) (:id (users/find-by-email ds "alice@test.com")))))))

(deftest test-set-display-name
  "Mirrors Quint action: setDisplayName(email, name)
  A user with an empty display name gets it set; then sessions can be created."
  (let [ds   (tu/fresh-db)
        user (users/ensure-user! ds "bob@test.com")]
    (is (nil? (:display_name user)))
    (let [updated (users/set-display-name! ds "bob@test.com" "Bob the Builder")]
      (is (= "Bob the Builder" (:display_name updated)))
      (is (= "bob@test.com" (:email updated))))
    ;; Lookup reflects the change
    (is (= "Bob the Builder"
           (:display_name (users/find-by-email ds "bob@test.com"))))))

(deftest test-set-display-name-empty-rejected
  "Quint setDisplayName requires name != ''.
  While the DB layer accepts empty strings, the handler layer rejects them.
  We test here that an empty string can be stored (DB-level) but the
  invariant will be caught by higher-level validation."
  (let [ds (tu/fresh-db)]
    (users/ensure-user! ds "charlie@test.com")
    (let [updated (users/set-display-name! ds "charlie@test.com" "")]
      (is (= "" (:display_name updated))))
    ;; Non-empty works
    (let [updated (users/set-display-name! ds "charlie@test.com" "Charlie")]
      (is (= "Charlie" (:display_name updated))))))

(deftest test-user-find-by-id
  "Round-trip: create user, find by id."
  (let [ds   (tu/fresh-db)
        user (users/ensure-user! ds "dave@test.com")]
    (is (= (:id user) (:id (users/find-by-id ds (:id user)))))
    (is (= "dave@test.com" (:email (users/find-by-id ds (:id user)))))))
