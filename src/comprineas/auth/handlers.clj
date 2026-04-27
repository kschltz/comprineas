(ns comprineas.auth.handlers
  (:require [clojure.string :as str]
            [comprineas.auth.tokens :as tokens]
            [comprineas.auth.rate-limit :as rate-limit]
            [comprineas.auth.users :as users]
            [comprineas.auth.sessions :as sessions]
            [comprineas.auth.mailer :as mailer]
            [comprineas.auth.password :as password]
            [comprineas.auth.reset-tokens :as reset-tokens]
            [selmer.parser :as selmer]
            [ring.util.response :as resp]))

(defn- base-url [{:keys [scheme headers server-name server-port]}]
  (str (name (or scheme :http)) "://" server-name
       (when (and server-port (not= server-port 80) (not= server-port 443))
         (str ":" server-port))))

(defn login-page [_req]
  (-> (selmer/render-file "auth/login.html" {})
      (resp/response)
      (resp/content-type "text/html")))

;; ── Password Authentication Handlers (PRD-0002) ──

(defn register-page [_req]
  (-> (selmer/render-file "auth/register.html" {})
      (resp/response)
      (resp/content-type "text/html")))

(defn post-register
  "POST /register — Create account with email, password, display name.
   FR-1, FR-2, FR-3."
  [{:keys [db] :as req}]
  (let [email        (get-in req [:params :email])
        pw           (get-in req [:params :password])
        display-name (get-in req [:params :display_name])]
    (cond
      ;; Validate email
      (or (nil? email) (not (re-matches #"[^\s@]+@[^\s@]+\.[^\s@]+" email)))
      (-> (selmer/render-file "auth/register.html"
                              {:error "Please enter a valid email address."
                               :email email :display_name display-name})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))

      ;; Validate password length (FR-1: ≥ 8 chars)
      (not (password/valid-password? pw))
      (-> (selmer/render-file "auth/register.html"
                              {:error "Password must be at least 8 characters."
                               :email email :display_name display-name})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))

      ;; Validate display name (FR-1: 1–50 printable characters)
      (or (nil? display-name) (str/blank? display-name) (> (count display-name) 50))
      (-> (selmer/render-file "auth/register.html"
                              {:error "Display name must be 1–50 characters."
                               :email email :display_name display-name})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))

      ;; Check duplicate email (FR-2)
      (users/find-by-email db email)
      (-> (selmer/render-file "auth/register.html"
                              {:error "This email is already registered."
                               :display_name display-name})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))

      ;; Success — create user with bcrypt hash, session, redirect (FR-3)
      :else
      (let [pw-hash (password/hash-password pw)
            user    (users/create-user-with-password! db email (str/trim display-name) pw-hash)
            session (sessions/create-session! db (:id user))]
        (-> (resp/redirect "/" :see-other)
            (assoc-in [:session :session-token] (:token session)))))))

(defn- get-ip
  "Extract the client IP from request, preferring X-Forwarded-For header."
  [req]
  (or (some-> (get-in req [:headers "x-forwarded-for"])
              (str/split #",")
              first
              str/trim)
      (:remote-addr req)))

(defn post-login-password
  "POST /login — Email + password login.
   FR-4, FR-5, FR-6, FR-7, FR-8. Anti-enumeration: same error for non-existent email vs wrong password."
  [{:keys [db] :as req}]
  (let [email (get-in req [:params :email])
        pw    (get-in req [:params :password])
        ip    (get-ip req)]
    ;; Rate limit check (FR-8)
    (if-not (rate-limit/within-login-limit? db ip)
      (-> (selmer/render-file "auth/login.html"
                              {:error "Too many attempts. Please wait a moment."
                               :email email})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 429))
      (let [_             (rate-limit/record-login-attempt! db ip)
            user          (users/find-by-email-with-password db email)
            generic-error (-> (selmer/render-file "auth/login.html"
                                                  {:error "Invalid email or password."
                                                   :email email})
                              (resp/response)
                              (resp/content-type "text/html")
                              (resp/status 401))]
        (cond
          ;; Non-existent email — perform dummy bcrypt compare for timing, then generic error (FR-5)
          (nil? user)
          (do (password/verify-password pw (password/hash-password "dummy-timing-password"))
              generic-error)

          ;; User has no password set (magic-link-only)
          (nil? (:password_hash user))
          generic-error

          ;; Correct password — create session, redirect (FR-6)
          (password/verify-password pw (:password_hash user))
          (let [session (sessions/create-session! db (:id user))]
            (-> (resp/redirect "/" :see-other)
                (assoc-in [:session :session-token] (:token session))))

          ;; Wrong password — same generic error (FR-7)
          :else
          generic-error)))))

(defn forgot-password-page [_req]
  (-> (selmer/render-file "auth/forgot-password.html" {})
      (resp/response)
      (resp/content-type "text/html")))

(defn post-forgot-password
  "POST /forgot-password — Generate reset token, send email.
   FR-9, FR-10. Anti-enumeration: same success message for all emails."
  [{:keys [db secrets mailer] :as req}]
  (let [email  (get-in req [:params :email])
        secret (:hmac secrets)]
    (if (or (nil? email) (not (re-matches #"[^\s@]+@[^\s@]+\.[^\s@]+" email)))
      ;; Invalid email format
      (-> (selmer/render-file "auth/forgot-password.html"
                              {:error "Please enter a valid email address."})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))
      ;; Generate token and send email regardless of whether user exists (FR-10)
      (let [signed (reset-tokens/create-reset-token! db email secret)
            link   (str (base-url req) "/reset-password/" signed)]
        ;; Only actually send the email if the user exists
        (when (users/find-by-email db email)
          (mailer/send-reset-link mailer email link))
        ;; Anti-enumeration: same success message (FR-10)
        (-> (selmer/render-file "auth/forgot-password-sent.html" {:email email})
            (resp/response)
            (resp/content-type "text/html"))))))

(defn reset-password-page
  "GET /reset-password/:token — Validate token, show reset form.
   FR-12."
  [{:keys [db secrets] :as req}]
  (let [signed   (get-in req [:path-params :token])
        secret   (:hmac secrets)
        payload  (tokens/unsign-payload signed secret)]
    (if (nil? payload)
      (-> (selmer/render-file "auth/error.html"
                              {:message "This link is invalid or has expired."})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))
      (let [{:keys [email token]} payload
            token-hash (tokens/hash-token token)
            db-token   (reset-tokens/find-valid-token db token-hash)]
        (if (nil? db-token)
          (-> (selmer/render-file "auth/error.html"
                                  {:message "This link is invalid or has expired."})
              (resp/response)
              (resp/content-type "text/html")
              (resp/status 400))
          ;; Valid token — show the reset form with the signed token for POST
          (-> (selmer/render-file "auth/reset-password.html"
                                  {:token signed})
              (resp/response)
              (resp/content-type "text/html")))))))

(defn post-reset-password
  "POST /reset-password/:token — Validate token, update password.
   FR-13."
  [{:keys [db secrets] :as req}]
  (let [signed   (get-in req [:path-params :token])
        secret   (:hmac secrets)
        pw       (get-in req [:params :password])
        payload  (tokens/unsign-payload signed secret)]
    (cond
      ;; Invalid or tampered token
      (nil? payload)
      (-> (selmer/render-file "auth/error.html"
                              {:message "This link is invalid or has expired."})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))

      ;; Password too short
      (not (password/valid-password? pw))
      (-> (selmer/render-file "auth/reset-password.html"
                              {:token signed
                               :error "Password must be at least 8 characters."})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))

      :else
      (let [{:keys [email token]} payload
            token-hash (tokens/hash-token token)
            db-token   (reset-tokens/find-valid-token db token-hash)]
        (cond
          ;; Token not found / expired / already used
          (nil? db-token)
          (-> (selmer/render-file "auth/error.html"
                                  {:message "This link is invalid or has expired."})
              (resp/response)
              (resp/content-type "text/html")
              (resp/status 400))

          ;; Valid token — update password, mark token used, redirect to login
          :else
          (let [_        (reset-tokens/mark-token-used! db (:id db-token))
                pw-hash  (password/hash-password pw)]
            (users/set-password-hash! db email pw-hash)
            (-> (resp/redirect "/login" :see-other)
                (assoc :flash "Password updated. Please log in."))))))))

(defn set-password-page
  "GET /set-password — Show set-password form for logged-in users without a password.
   FR-14."
  [{:keys [current-user] :as req}]
  (if (nil? current-user)
    (-> (resp/redirect "/login" :see-other))
    (if (:password_hash current-user)
      ;; Already has a password — redirect home
      (resp/redirect "/" :see-other)
      ;; Show set-password form
      (-> (selmer/render-file "auth/set-password.html" {})
          (resp/response)
          (resp/content-type "text/html")))))

(defn post-set-password
  "POST /set-password — Set password for logged-in user without one.
   FR-14."
  [{:keys [db current-user] :as req}]
  (let [pw (get-in req [:params :password])]
    (cond
      ;; Not logged in
      (nil? current-user)
      (resp/redirect "/login" :see-other)

      ;; Already has a password
      (:password_hash current-user)
      (resp/redirect "/" :see-other)

      ;; Password too short
      (not (password/valid-password? pw))
      (-> (selmer/render-file "auth/set-password.html"
                              {:error "Password must be at least 8 characters."})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))

      ;; Success — hash and store
      :else
      (let [pw-hash (password/hash-password pw)]
        (users/set-password-hash! db (:email current-user) pw-hash)
        (-> (resp/redirect "/" :see-other)
            (assoc :flash "Password set successfully."))))))

(defn request-magic-link [{:keys [db secrets mailer] :as req}]
  (let [email (get-in req [:params :email])
        ds    db
        secret (:hmac secrets)]
    (cond
      (or (nil? email) (not (re-matches #"[^\s@]+@[^\s@]+\.[^\s@]+" email)))
      (-> (selmer/render-file "auth/login.html" {:error "Please enter a valid email address."})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))

      (not (rate-limit/within-limit? ds email))
      (-> (selmer/render-file "auth/login.html"
                              {:email email :error "Too many requests. Please wait 15 minutes."})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 429))

      :else
      (let [signed (tokens/create-magic-token! ds email secret)
            link   (str (base-url req) "/magic-link/" signed)]
        (mailer/send-link mailer email link)
        ;; Anti-enumeration: same message regardless of email validity
        (-> (selmer/render-file "auth/email-sent.html" {:email email})
            (resp/response)
            (resp/content-type "text/html"))))))

(defn handle-magic-link [{:keys [db secrets] :as req}]
  (let [signed (get-in req [:path-params :token])
        ds     db
        secret (:hmac secrets)
        payload (tokens/unsign-payload signed secret)]
    (if (nil? payload)
      (-> (selmer/render-file "auth/error.html" {:message "This link is invalid or has expired."})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))
      (let [{:keys [email token]} payload
            token-hash (tokens/hash-token token)
            db-token   (tokens/find-valid-token ds token-hash)]
        (if (nil? db-token)
          (-> (selmer/render-file "auth/error.html" {:message "This link has expired or already been used."})
              (resp/response)
              (resp/content-type "text/html")
              (resp/status 400))
          (let [_    (tokens/mark-token-used! ds (:id db-token))
                user (users/ensure-user! ds email)]
            (if (seq (:display_name user))
              ;; Returning user with display name → create session & redirect
              (let [session (sessions/create-session! ds (:id user))]
                (-> (resp/redirect "/" :see-other)
                    (assoc-in [:session :session-token] (:token session))))
              ;; First-time user → display name form
              (-> (selmer/render-file "auth/display-name.html" {:email email})
                  (resp/response)
                  (resp/content-type "text/html")))))))))

(defn post-display-name [{:keys [db] :as req}]
  (let [email (get-in req [:params :email])
        name  (get-in req [:params :display_name])]
    (cond
      (or (nil? name) (str/blank? name) (> (count name) 50))
      (-> (selmer/render-file "auth/display-name.html"
                              {:email email
                               :error "Display name must be 1–50 characters."})
          (resp/response)
          (resp/content-type "text/html")
          (resp/status 400))

      :else
      (let [user    (users/set-display-name! db email (str/trim name))
            session (sessions/create-session! db (:id user))]
        (-> (resp/redirect "/" :see-other)
            (assoc-in [:session :session-token] (:token session)))))))

(defn logout [{:keys [db] :as req}]
  (let [session-token (get-in req [:session :session-token])]
    (when session-token
      (when-let [session (sessions/find-by-token db session-token)]
        (sessions/destroy-session! db (:id session))))
    (-> (resp/redirect "/" :see-other)
        (assoc :session nil))))
