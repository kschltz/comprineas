(ns comprineas.auth.mailer
  "Email sending for authentication flows.
   Supports magic link and password reset emails.
   Uses stub mailer for development; swap implementation for production."
  (:require [integrant.core :as ig]))

(defprotocol Mailer
  (send-magic-link [this email link])
  (send-password-reset [this email link]))

(defrecord StubMailer []
  Mailer
  (send-magic-link [_ email link]
    (println (str "[STUB MAILER] Magic link to " email ": " link)))
  (send-password-reset [_ email link]
    (println (str "[STUB MAILER] Password reset link to " email ": " link))))

(defmethod ig/init-key :auth/mailer [_ {:keys [stub?]}]
  (->StubMailer))

(defn send-link [mailer email link]
  (send-magic-link mailer email link))

(defn send-reset-link [mailer email link]
  (send-password-reset mailer email link))