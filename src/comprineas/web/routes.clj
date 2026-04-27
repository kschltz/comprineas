(ns comprineas.web.routes
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [comprineas.auth.handlers :as auth]
            [comprineas.auth.middleware :as auth-mw]
            [comprineas.lists.handlers :as lists]
            [comprineas.lists.join :as join]
            [comprineas.lists.copy :as copy]
            [comprineas.lists.sse :as sse]
            [comprineas.items.handlers :as items]))

(defn routes []
  [["/login"
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
   ["/list/:code/items"
    {:post {:handler items/add-item!}}]
   ["/list/:code/items/:id/check"
    {:post {:handler items/toggle-check!}}]
   ["/list/:code/items/:id"
    {:delete {:handler items/delete-item!}}]
   ["/list/:code/events"
    {:get {:handler sse/sse-handler}}]
   ["/logout"
    {:post {:handler auth/logout}}]])

(defn wrap-deps [handler deps]
  (fn [req] (handler (merge req deps))))

(defn app [{:keys [db secrets mailer]}]
  (-> (ring/ring-handler
       (ring/router
        (routes)
        {:data {:muuntaja   m/instance
                :middleware [parameters/parameters-middleware
                             muuntaja/format-middleware]}})
       (ring/routes
        (ring/redirect-trailing-slash-handler)
        (ring/create-default-handler)))
      (wrap-deps {:db db :secrets secrets :mailer mailer})
      (wrap-session {:cookie-attrs {:http-only true
                                    :same-site :strict
                                    :secure false} ;; set true in production with HTTPS
                     :cookie-name "comprineas.session"})
      (auth-mw/wrap-auth db)
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))))
