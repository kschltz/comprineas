(ns comprineas.auth.tokens
  (:require [comprineas.db.core :as db]
            [buddy.sign.jwt :as jwt]
            [buddy.core.codecs :as codecs]
            [buddy.core.nonce :as nonce])
  (:import [java.security MessageDigest]))

(defn random-token []
  (-> (nonce/random-bytes 32)
      (codecs/bytes->hex)))

(defn hash-token [raw-token]
  (-> (doto (MessageDigest/getInstance "SHA-256")
             (.update (.getBytes raw-token "UTF-8")))
      (.digest)
      (codecs/bytes->hex)))

(defn sign-payload [payload secret]
  (jwt/sign payload secret {:alg :hs256}))

(defn unsign-payload [signed secret]
  (try
    (jwt/unsign signed secret {:alg :hs256})
    (catch Exception _ nil)))

(defn create-magic-token! [ds email secret]
  (let [raw     (random-token)
        hashed  (hash-token raw)]
    (db/execute-one! ds
      ["INSERT INTO magic_link_tokens (email, token_hash, expires_at)
        VALUES (?, ?, now() + INTERVAL '15 minutes')"
       email hashed])
    (sign-payload {:email email :token raw :iat (quot (System/currentTimeMillis) 1000)} secret)))

(defn find-valid-token [ds token-hash]
  (db/execute-one! ds
    ["SELECT id, email, token_hash, expires_at, used_at
      FROM magic_link_tokens
      WHERE token_hash = ?
        AND expires_at > now()
        AND used_at IS NULL"
     token-hash]))

(defn mark-token-used! [ds id]
  (db/execute-one! ds
    ["UPDATE magic_link_tokens SET used_at = now() WHERE id = ?" id]))
