(ns comprineas.auth.password
  "Password hashing and verification using bcrypt.
   Per ADR-0007: bcrypt cost factor >= 10, minimum password length 8 characters."
  (:require [buddy.hashers :as hashers]))

(def ^:private bcrypt-options {:alg :bcrypt+sha512
                               :cost 10})

(defn hash-password
  "Hash a plaintext password using bcrypt (cost factor 10).
   Returns the bcrypt hash string suitable for storing in the database."
  [plaintext]
  (hashers/derive plaintext bcrypt-options))

(defn verify-password
  "Verify a plaintext password against a stored bcrypt hash.
   Returns true if the password matches, false otherwise.
   Returns false for nil hash (anti-enumeration safe — same falsy behavior as wrong password)."
  [plaintext hash]
  (if hash
    (hashers/check plaintext hash)
    false))

(defn valid-password?
  "Check whether a plaintext password meets the minimum length requirement (>= 8 characters).
   Per PRD-0002, no other complexity rules are required."
  [plaintext]
  (and (string? plaintext)
       (>= (count plaintext) 8)))