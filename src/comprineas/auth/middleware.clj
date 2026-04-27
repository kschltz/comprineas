(ns comprineas.auth.middleware
  (:require [comprineas.auth.sessions :as sessions]
            [comprineas.auth.users :as users]))

(defn wrap-auth
  "Looks up session from cookie token and attaches :current-user to request.
   Includes password_hash so handlers can check whether user has a password set."
  [handler db]
  (fn [req]
    (let [token   (get-in req [:session :session-token])
          session (when token (sessions/find-by-token db token))
          user    (when session (users/find-by-id-with-password db (:user_id session)))]
      (handler (assoc req :current-user user :current-session session)))))
