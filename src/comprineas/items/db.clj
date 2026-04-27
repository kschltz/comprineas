(ns comprineas.items.db
  (:require [comprineas.db.core :as db]))

(defn add-item!
  "Insert a new item into list_items with the next position.
   Position is assigned as COALESCE(MAX(position), 0) + 1.
   Returns the created row with all columns."
  [ds list-id name quantity observations]
  (db/execute-one! ds
                   ["INSERT INTO list_items (list_id, name, quantity, observations, position, checked)
      VALUES (?, ?, ?, ?, COALESCE((SELECT MAX(position) FROM list_items WHERE list_id = ?), 0) + 1, false)
      RETURNING id, list_id, name, quantity, observations, checked, position, created_at, updated_at"
                    list-id name quantity observations list-id]))

(defn find-items-by-list
  "Returns all items for a list ordered by checked ASC, position ASC, id ASC."
  [ds list-id]
  (db/execute! ds
               ["SELECT id, list_id, name, quantity, observations, checked, position, created_at, updated_at
      FROM list_items WHERE list_id = ? ORDER BY checked ASC, position ASC, id ASC"
                list-id]))

(defn find-item-by-id
  "Returns a single item by id, or nil if not found."
  [ds id]
  (db/execute-one! ds
                   ["SELECT id, list_id, name, quantity, observations, checked, position, created_at, updated_at
      FROM list_items WHERE id = ?"
                    id]))

(defn toggle-check!
  "Toggle the checked boolean of an item. Returns the updated row."
  [ds id]
  (db/execute-one! ds
                   ["UPDATE list_items SET checked = NOT checked, updated_at = now() WHERE id = ?
      RETURNING id, list_id, name, quantity, observations, checked, position, created_at, updated_at"
                    id]))

(defn delete-item!
  "Delete an item by id. Returns the deleted row (or nil if not found)."
  [ds id]
  (db/execute-one! ds
                   ["DELETE FROM list_items WHERE id = ? RETURNING id"
                    id]))

(defn count-items-by-list
  "Returns the number of items in a list."
  [ds list-id]
  (:count (db/execute-one! ds
                           ["SELECT COUNT(*) as count FROM list_items WHERE list_id = ?"
                            list-id])))

(defn max-position
  "Returns the maximum position value for a list, or nil if empty."
  [ds list-id]
  (:max_position (db/execute-one! ds
                                  ["SELECT MAX(position) as max_position FROM list_items WHERE list_id = ?"
                                   list-id])))
