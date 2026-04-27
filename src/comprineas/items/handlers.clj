(ns comprineas.items.handlers
  (:require [clojure.string :as str]
            [comprineas.items.db :as items-db]
            [comprineas.lists.db :as lists-db]
            [comprineas.lists.sse :as sse]
            [selmer.parser :as selmer]
            [ring.util.response :as resp]))

(defn render-item-row
  "Render HTML for a single item row (for direct responses and SSE fragments)."
  [item list]
  (selmer/render-file "items/item-row.html" {:item item :list list}))

(defn render-item-list
  "Render HTML for the full item list container innerHTML."
  [items list]
  (selmer/render-file "items/item-list.html" {:items items :list list}))

(defn- validate-item-inputs
  "Validate item inputs. Returns nil on success, error string on failure."
  [name quantity observations]
  (cond
    (or (str/blank? name) (> (count name) 255))
    "Name must be 1–255 characters."

    (> (count quantity) 50)
    "Quantity must be at most 50 characters."

    (> (count observations) 2000)
    "Observations must be at most 2000 characters."

    :else nil))

(defn add-item!
  "POST /list/:code/items
   FR-1, FR-2, FR-3, FR-8, FR-9, FR-14, FR-16, FR-18."
  [{:keys [db] :as req}]
  (let [code (get-in req [:path-params :code])
        list (lists-db/find-by-code db code)]
    (cond
      (nil? list)
      (-> (resp/response "List not found.")
          (resp/status 404))

      (not= "active" (:status list))
      (-> (resp/response "This list is not active.")
          (resp/status 409))

      :else
      (let [name         (str/trim (get-in req [:params :name] ""))
            quantity     (str/trim (get-in req [:params :quantity] ""))
            observations (str/trim (get-in req [:params :observations] ""))
            error        (validate-item-inputs name quantity observations)]
        (if error
          (-> (str "<div id=\"add-item-error\" hx-swap-oob=\"innerHTML\">" error "</div>")
              (resp/response)
              (resp/content-type "text/html")
              (resp/status 400))
          (let [item (items-db/add-item! db (:id list) name quantity observations)]
            (sse/broadcast! db code "item-added"
                            (assoc (select-keys item [:id :list_id :name :quantity :observations :checked :position :created_at])
                                   :html (str "<div id=\"item-list\" hx-swap-oob=\"beforeend\">"
                                              (render-item-row item list)
                                              "</div>")))
            (-> (render-item-row item list)
                (resp/response)
                (resp/content-type "text/html"))))))))

(defn toggle-check!
  "POST /list/:code/items/:id/check
   FR-4, FR-8, FR-10, FR-16."
  [{:keys [db] :as req}]
  (let [code (get-in req [:path-params :code])
        id   (parse-long (get-in req [:path-params :id]))
        list (lists-db/find-by-code db code)]
    (cond
      (nil? list)
      (-> (resp/response "List not found.")
          (resp/status 404))

      (not= "active" (:status list))
      (-> (resp/response "This list is not active.")
          (resp/status 409))

      :else
      (let [item (items-db/find-item-by-id db id)]
        (if (nil? item)
          (-> (resp/response "Item not found.")
              (resp/status 404))
          (let [updated (items-db/toggle-check! db id)
                items   (items-db/find-items-by-list db (:id list))]
            (sse/broadcast! db code "item-updated"
                            (assoc (select-keys updated [:id :list_id :name :quantity :observations :checked :position :created_at])
                                   :html (str "<div id=\"item-list\" hx-swap-oob=\"innerHTML\">"
                                              (render-item-list items list)
                                              "</div>")))
            (-> (render-item-list items list)
                (resp/response)
                (resp/content-type "text/html"))))))))

(defn delete-item!
  "DELETE /list/:code/items/:id
   FR-5, FR-8, FR-11, FR-16."
  [{:keys [db] :as req}]
  (let [code (get-in req [:path-params :code])
        id   (parse-long (get-in req [:path-params :id]))
        list (lists-db/find-by-code db code)]
    (cond
      (nil? list)
      (-> (resp/response "List not found.")
          (resp/status 404))

      (not= "active" (:status list))
      (-> (resp/response "This list is not active.")
          (resp/status 409))

      :else
      (let [item (items-db/find-item-by-id db id)]
        (when item
          (items-db/delete-item! db id)
          (sse/broadcast! db code "item-deleted"
                          {:id (:id item)
                           :html (str "<div id=\"item-" (:id item) "\" hx-swap-oob=\"delete\"></div>")}))
        (-> (resp/response "")
            (resp/content-type "text/html"))))))

(defn items-list-page
  "GET /list/:code/items-list — Return full item list HTML fragment."
  [{:keys [db] :as req}]
  (let [code (get-in req [:path-params :code])
        lst  (lists-db/find-by-code db code)]
    (if (or (nil? lst) (not= "active" (:status lst)))
      (-> (resp/response "<p class='text-gray-400'>No items yet.</p>")
          (resp/content-type "text/html"))
      (let [items (items-db/find-items-by-list db (:id lst))]
        (-> (render-item-list items lst)
            (resp/response)
            (resp/content-type "text/html"))))))
