(ns comprineas.lists.db
  "Database operations for shared lists (PRD-0003, 0004, 0005, 0006).
   Uses next.jdbc for queries and jdbc for transactions."
  (:require [comprineas.db.core :as db]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))

;; ──────────────────────────────────────────────────────────
;; Read operations
;; ──────────────────────────────────────────────────────────

(defn find-by-code
  "Fetch a list by its 6-character share code (case-insensitive)."
  [ds code]
  (db/execute-one! ds ["SELECT * FROM lists WHERE LOWER(code) = LOWER(?)" code]))

(defn find-by-id
  "Fetch a list by its id."
  [ds id]
  (db/execute-one! ds ["SELECT * FROM lists WHERE id = ?" id]))

(defn is-participant?
  "Check whether a user is a participant of a list."
  [ds list-id user-id]
  (some? (db/execute-one! ds ["SELECT 1 FROM list_participants WHERE list_id = ? AND user_id = ?"
                               list-id user-id])))

(defn find-participants-by-list
  "Fetch all participants for a given list."
  [ds list-id]
  (db/execute! ds ["SELECT * FROM list_participants WHERE list_id = ? ORDER BY joined_at" list-id]))

(defn user-lists
  "Fetch all lists visible to a user (created by or participated in), excluding deleted.
   Returns lists ordered by created_at DESC."
  [ds user-id]
  (db/execute! ds [(str "SELECT DISTINCT l.* FROM lists l "
                        "LEFT JOIN list_participants lp ON l.id = lp.list_id "
                        "WHERE (l.created_by = ? OR lp.user_id = ?) "
                        "AND l.status != 'deleted' "
                        "ORDER BY l.created_at DESC")
                   user-id user-id]))

(defn get-completed-list
  "Fetch the completed_lists archive record for a given original_list_id."
  [ds list-id]
  (db/execute-one! ds ["SELECT * FROM completed_lists WHERE original_list_id = ?" list-id]))

;; ──────────────────────────────────────────────────────────
;; Write operations
;; ──────────────────────────────────────────────────────────

(defn create-list!
  "Create a new list with the given code, name, and creator.
   Returns the created list row."
  [ds code name created-by]
  (db/execute-one! ds ["INSERT INTO lists (code, name, status, version, created_by)
                       VALUES (?, ?, 'active', 1, ?) RETURNING *"
                      code name created-by]))

(defn add-participant!
  "Add a user as a participant of a list.
   May throw on UNIQUE constraint violation — callers should handle that."
  [ds list-id user-id]
  (db/execute-one! ds ["INSERT INTO list_participants (list_id, user_id) VALUES (?, ?)"
                       list-id user-id]))

(defn rename-list!
  "Rename a list with optimistic locking (version check).
   Returns the number of rows affected (0 = conflict, 1 = success)."
  [ds list-id new-name expected-version]
  (let [result (jdbc/execute-one! ds
                  ["UPDATE lists SET name = ?, version = version + 1, updated_at = now()
                    WHERE id = ? AND version = ?"
                   new-name list-id expected-version]
                  {:return-keys true})]
    (if result 1 0)))

(defn complete-list!
  "Complete a list with optimistic locking. Also archives items to completed_lists.
   Returns the updated list row, or nil if version mismatch."
  [ds list-id expected-version]
  (jdbc/with-transaction [tx ds]
    (let [list-row (db/execute-one! tx ["SELECT * FROM lists WHERE id = ? AND version = ?"
                                         list-id expected-version])]
      (when list-row
        ;; Archive items to completed_lists
        (let [items (db/execute! tx ["SELECT * FROM list_items WHERE list_id = ? ORDER BY checked ASC, position ASC, id ASC"
                                     list-id])
              archived-data (mapv (fn [item]
                                    {:name        (:name item)
                                     :quantity    (or (:quantity item) "")
                                     :observations (or (:observations item) "")
                                     :checked     (:checked item)
                                     :position    (:position item)})
                                  items)]
          (db/execute-one! tx ["INSERT INTO completed_lists (original_list_id, code, name, completed_at, archived_data)
                               VALUES (?, ?, ?, now(), ?::jsonb)"
                               list-id (:code list-row) (:name list-row)
                               (cheshire.core/generate-string archived-data)])
          ;; Update list status
          (db/execute-one! tx ["UPDATE lists SET status = 'completed', version = version + 1,
                               completed_at = now(), updated_at = now()
                               WHERE id = ? AND version = ?"
                               list-id expected-version]))))))

(defn delete-list!
  "Soft-delete a list with optimistic locking.
   Returns 1 if successful, 0 if version mismatch."
  [ds list-id expected-version]
  (let [result (jdbc/execute-one! ds
                  ["UPDATE lists SET status = 'deleted', version = version + 1, updated_at = now()
                    WHERE id = ? AND version = ?"
                   list-id expected-version])]
    (if result 1 0)))