(ns comprineas.web.routes
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [comprineas.auth.handlers :as auth]
            [comprineas.auth.middleware :as auth-mw]
            [comprineas.lists.handlers :as lists]
            [comprineas.lists.join :as join]
            [comprineas.lists.copy :as copy]
            [comprineas.lists.sse :as sse]
            [comprineas.items.handlers :as items]
            [ring.util.response :as resp]))

(defn routes []
  [["/"
    {:get {:handler (fn [_] (resp/redirect "/dashboard" :see-other))}}]
   ["/login"
    {:get  {:handler auth/login-page}
     :post {:handler auth/request-magic-link}}]
   ["/login/password"
    {:post {:handler auth/post-login-password}}]
   ["/register"
    {:get  {:handler auth/register-page}
     :post {:handler auth/post-register}}]
   ["/forgot-password"
    {:get  {:handler auth/forgot-password-page}
     :post {:handler auth/post-forgot-password}}]
   ["/reset-password/:token"
    {:get  {:handler auth/reset-password-page}
     :post {:handler auth/post-reset-password}}]
   ["/set-password"
    {:get  {:handler auth/set-password-page}
     :post {:handler auth/post-set-password}}]
   ["/magic-link/:token"
    {:get {:handler auth/handle-magic-link}}]
   ["/display-name"
    {:post {:handler auth/post-display-name}}]
   ["/dashboard"
    {:get {:handler lists/dashboard-page}}]
   ["/lists"
    {:post {:handler lists/create-list!}}]
   ["/join"
    {:post {:handler join/join-list!}}]
   ["/list/:code"
    {:get {:handler lists/list-view}}]
   ["/list/:code/complete"
    {:post {:handler lists/complete-list!}}]
   ["/list/:code/rename"
    {:post {:handler lists/rename-list!}}]
   ["/list/:code/copy"
    {:get  {:handler copy/copy-list-page}
     :post {:handler copy/copy-list!}}]
   ["/list/:code/items-list"
    {:get {:handler items/items-list-page}}]
   ["/list/:code/items"
    {:post {:handler items/add-item!}}]
   ["/list/:code/items/:id/check"
    {:post {:handler items/toggle-check!}}]
   ["/list/:code/items/:id"
    {:delete {:handler items/delete-item!}}]
   ["/logout"
    {:post {:handler auth/logout}}]])

;; SSE routes are handled separately — they need deps injection
;; and auth but must NOT go through wrap-defaults (which corrupts
;; http-kit async channel responses).
(defn sse-routes []
  [["/dashboard/events"
    {:get {:handler sse/dashboard-sse-handler}}]
   ["/list/:code/events"
    {:get {:handler sse/sse-handler}}]])

(defn wrap-deps [handler deps]
  (fn [req] (handler (merge req deps))))

(defn app [{:keys [db secrets mailer]}]
  (let [deps {:db db :secrets secrets :mailer mailer}
        ;; Main app with standard middleware stack
        main-handler (-> (ring/ring-handler
                          (ring/router
                           (routes)
                           {:data {:muuntaja   m/instance
                                   :middleware [parameters/parameters-middleware
                                                muuntaja/format-middleware]}})
                          (ring/routes
                           (ring/redirect-trailing-slash-handler)
                           (ring/create-default-handler)))
                         (wrap-deps deps)
                         (auth-mw/wrap-auth db)
                         (wrap-defaults (-> site-defaults
                                            (assoc-in [:security :anti-forgery] false)
                                            (assoc :websocket nil))))
        ;; SSE handler with minimal middleware (no wrap-defaults to avoid corrupting async responses)
        sse-handler (-> (ring/ring-handler
                         (ring/router (sse-routes))
                         (ring/create-default-handler))
                        (wrap-deps deps)
                        (auth-mw/wrap-auth db))]
    ;; Route requests: SSE paths go to sse-handler, everything else to main-handler
    (fn [req]
      (let [uri (:uri req)]
        (when (or (= uri "/dashboard/events")
                  (re-matches #"/list/[A-Za-z0-9]+/events" uri))
          (println "[ROUTER] SSE request:" uri))
        (if (or (= uri "/dashboard/events")
                (re-matches #"/list/[A-Za-z0-9]+/events" uri))
          (sse-handler req)
          (main-handler req))))))