(ns comprineas.lists.handlers
  "List page handlers: dashboard, list-view, create, complete.
   PRD-0003 FR-1–FR-12, PRD-0004, PRD-0006."
  (:require [clojure.string :as str]
            [comprineas.lists.db :as db]
            [comprineas.lists.codes :as codes]
            [comprineas.lists.sse :as sse]
            [comprineas.items.db :as items-db]
            [selmer.parser :as selmer]
            [ring.util.response :as resp]))

;; ──────────────────────────────────────────────────────────
;; GET /dashboard — "My Lists" landing page
;; ──────────────────────────────────────────────────────────

(defn dashboard-page
  "Render the user's list dashboard showing active and past lists,
   plus create-list and join-by-code forms."
  [{:keys [db current-user]}]
  (if-not (:id current-user)
    (resp/redirect "/login" :see-other)
    (let [lists (db/user-lists db (:id current-user))]
      (-> (selmer/render-file "lists/dashboard.html"
                              {:user       current-user
                               :lists      lists
                               :active-lists  (filterv #(= "active" (:status %)) lists)
                               :completed-lists (filterv #(= "completed" (:status %)) lists)})
          (resp/response)
          (resp/content-type "text/html")))))

;; ──────────────────────────────────────────────────────────
;; GET /list/:code — View a specific shared list
;; ──────────────────────────────────────────────────────────

(defn list-view
  "Render the full list page: item input, item list, share code, actions."
  [{:keys [db current-user] :as req}]
  (let [code (get-in req [:path-params :code])
        lst  (db/find-by-code db code)]
    (cond
      (nil? lst)
      {:status 404 :headers {"Content-Type" "text/html"}
       :body (str "<h1>List not found</h1><p>No list with code <strong>" code "</strong> exists.</p>"
                  "<a href=\"/dashboard\">Back to My Lists</a>")}

      (not= "active" (:status lst))
      {:status 410 :headers {"Content-Type" "text/html"}
       :body (str "<h1>List unavailable</h1><p>This list is no longer active.</p>"
                  "<a href=\"/dashboard\">Back to My Lists</a>")}

      :else
      (let [items        (items-db/find-items-by-list db (:id lst))
            participants (db/find-participants-by-list db (:id lst))
            is-creator?  (= (:id current-user) (:created_by lst))]
        (-> (selmer/render-file "lists/list-view.html"
                                {:user         current-user
                                 :list         lst
                                 :items        items
                                 :participants participants
                                 :is-creator?  is-creator?})
            (resp/response)
            (resp/content-type "text/html"))))))

;; ──────────────────────────────────────────────────────────
;; POST /lists — Create a new shared list
;; ──────────────────────────────────────────────────────────

(defn create-list!
  "Create a new list, add creator as participant, redirect to list page."
  [{:keys [db current-user] :as req}]
  (if-not (:id current-user)
    (resp/redirect "/login" :see-other)
    (let [name (str/trim (get-in req [:params :name] ""))]
      (if (str/blank? name)
        {:status 400 :headers {"Content-Type" "text/html"}
         :body (selmer/render-file "lists/dashboard.html"
                                   {:user  current-user
                                    :error "List name is required."
                                    :lists (db/user-lists db (:id current-user))})}
        (let [code (codes/generate-unique-code db)
              lst  (db/create-list! db code name (:id current-user))]
          (db/add-participant! db (:id lst) (:id current-user))
          (sse/broadcast-dashboard! db "list-created"
                                    {:list_id (:id lst) :code code :name name})
          (resp/redirect (str "/list/" code) :see-other))))))

;; ──────────────────────────────────────────────────────────
;; POST /list/:code/complete — Complete (archive) a list
;; ──────────────────────────────────────────────────────────

(defn complete-list!
  "Complete a list, archiving its items and moving it to past lists."
  [{:keys [db current-user] :as req}]
  (let [code (get-in req [:path-params :code])
        lst  (db/find-by-code db code)]
    (cond
      (nil? lst)
      {:status 404 :body "List not found."}

      (not= "active" (:status lst))
      {:status 409 :body "This list is not active."}

      :else
      (let [result (db/complete-list! db (:id lst) (:version lst))]
        (if result
          (do
            (sse/broadcast! db code "list-updated"
                            {:list_id (:id lst) :status "completed"
                             :html (str "<div id=\"list-status\" hx-swap-oob=\"innerHTML\">"
                                        "This list has been completed.</div>")})
            (resp/redirect "/dashboard" :see-other))
          {:status 409 :body "The list was modified by someone else. Please refresh and try again."})))))

;; ──────────────────────────────────────────────────────────
;; POST /list/:code/rename — Rename a list (inline from list-view)
;; ──────────────────────────────────────────────────────────

(defn rename-list!
  "POST /list/:code/rename — Rename a list with optimistic locking."
  [{:keys [db current-user] :as req}]
  (let [code     (get-in req [:path-params :code])
        lst      (db/find-by-code db code)
        new-name (str/trim (get-in req [:params :name] ""))]
    (cond
      (nil? lst)
      {:status 404 :body "List not found."}

      (str/blank? new-name)
      {:status 400 :body "Name cannot be empty."}

      :else
      (let [result (db/rename-list! db (:id lst) new-name (:version lst))]
        (if (pos? result)
          (do
            (sse/broadcast! db code "list-updated"
                            {:list_id (:id lst) :name new-name})
            (-> (str "<span id=\"list-name\" hx-swap-oob=\"innerHTML\">" new-name "</span>")
                (resp/response)
                (resp/content-type "text/html")))
          {:status 409 :body "The list was modified by someone else. Please refresh and try again."}))))))
