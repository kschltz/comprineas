(ns comprineas.lists.copy
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [next.jdbc :as jdbc]
            [selmer.parser :as selmer]
            [ring.util.response :as resp]
            [comprineas.lists.codes :as codes]
            [comprineas.lists.sse :as sse]
            [comprineas.lists.db :as lists-db]))

;; ── Helpers ─────────────────────────────────────────────────────────

(defn- ->json-string
  "Coerce a PostgreSQL JSONB column value to a JSON string."
  [v]
  (cond
    (nil? v) nil
    (string? v) v
    :else (str v)))

(defn parse-archived-items
  "Parse JSONB array from archived_data.
   Each element has keys: name, quantity, observations, checked, position.
   Extract name, quantity, observations; discard checked; assign new
   sequential positions 1..N following archive order."
  [archived-data]
  (let [json-str (->json-string archived-data)]
    (when json-str
      (map-indexed
       (fn [idx item]
         {:name (:name item)
          :quantity (:quantity item)
          :observations (:observations item)
          :position (inc idx)})
       (json/parse-string json-str true)))))

(defn- default-copy-name
  "Generate the default copy name.  If the original name plus suffix
   would exceed 100 characters, truncate the original name to 93 chars
   before appending the 7-char suffix."
  [original-name]
  (let [suffix " (Copy)"
        max-len 100]
    (if (<= (+ (count original-name) (count suffix)) max-len)
      (str original-name suffix)
      (let [truncate-to (- max-len (count suffix))]
        (str (subs original-name 0 (min (count original-name) truncate-to))
             suffix)))))

(defn- validate-name
  "Validate list name. Returns nil if valid, error string otherwise."
  [name]
  (cond
    (or (nil? name) (str/blank? name))
    "Name is required."

    (> (count name) 100)
    "Name must be 100 characters or fewer."

    :else nil))

(defn- render-modal
  "Render the copy-modal.html Selmer template as a Ring response."
  [code original-name error]
  (-> (selmer/render-file "lists/copy-modal.html"
                          {:code code
                           :original_name original-name
                           :default_name (default-copy-name original-name)
                           :error error})
      (resp/response)
      (resp/content-type "text/html")))

(defn- htmx-redirect
  "Return a Ring response with the HX-Redirect header for HTMX."
  [url]
  (-> (resp/response "")
      (resp/header "HX-Redirect" url)))

;; ── Handlers ────────────────────────────────────────────────────────

(defn copy-list-page
  "GET /list/:code/copy — Show the copy confirmation modal.
   Returns 404 if the list does not exist or is not completed."
  [{:keys [db] :as _req}]
  (let [code (get-in _req [:path-params :code])
        original (lists-db/find-by-code db code)]
    (cond
      (nil? original)
      (-> (resp/response "List not found.")
          (resp/status 404))

      (not= "completed" (:status original))
      (-> (resp/response "List not found.")
          (resp/status 404))

      :else
      (render-modal code (:name original) nil))))

(defn copy-list!
  "POST /list/:code/copy — Copy a completed list into a new active list.
   Validates the original list, archived data, and name; performs the
   copy atomically inside a DB transaction; broadcasts SSE; redirects."
  [{:keys [db current-user] :as _req}]
  (let [code (get-in _req [:path-params :code])
        original (lists-db/find-by-code db code)
        user-id (:id current-user)
        submitted-name (get-in _req [:params :name])]
    (cond
      ;; Not authenticated
      (nil? current-user)
      (-> (resp/response "You must be logged in to copy a list.")
          (resp/status 401))

      ;; Original list missing or not completed (FR-10)
      (or (nil? original) (not= "completed" (:status original)))
      (-> (resp/response "List not found.")
          (resp/status 404))

      :else
      (let [name (when submitted-name (str/trim submitted-name))
            name-error (validate-name name)]
        (if name-error
          ;; Validation failure — re-render modal with inline error (AC-12)
          (render-modal code (:name original) name-error)

          ;; Proceed with copy
          (let [archive (lists-db/get-completed-list db (:id original))]
            (cond
              ;; Missing archive data (FR-9)
              (or (nil? archive) (nil? (:archived_data archive)))
              (-> (resp/response "Completed list data not found.")
                  (resp/status 404))

              ;; Atomic copy inside transaction (FR-13)
              :else
              (try
                (jdbc/with-transaction [tx db]
                  (let [new-code (codes/generate-unique-code tx)
                        new-list (jdbc/execute-one!
                                  tx
                                  ["INSERT INTO lists (code, name, status, version, created_by, copied_from)
                                    VALUES (?, ?, 'active', 1, ?, ?)
                                    RETURNING *"
                                   new-code name user-id (:id original)])
                        new-list-id (:id new-list)
                        items (parse-archived-items (:archived_data archive))]

                    ;; Insert copied items (FR-4)
                    (doseq [item items]
                      (jdbc/execute-one!
                       tx
                       ["INSERT INTO list_items (list_id, name, quantity, observations, checked, position)
                         VALUES (?, ?, ?, ?, false, ?)"
                        new-list-id
                        (:name item)
                        (:quantity item)
                        (:observations item)
                        (:position item)]))

                    ;; Add copier as participant (FR-5)
                    (jdbc/execute-one!
                     tx
                     ["INSERT INTO list_participants (list_id, user_id) VALUES (?, ?)
                       ON CONFLICT DO NOTHING"
                      new-list-id user-id])

                    ;; Broadcast SSE so dashboard updates in real time (FR-8)
                    (sse/broadcast! "list-created" {:code new-code :name name})

                    ;; Redirect to the new list (FR-3)
                    (htmx-redirect (str "/list/" new-code))))

                (catch Exception _e
                  (render-modal code (:name original)
                                "An error occurred while copying the list."))))))))))
