(ns comprineas.lists.join
  "Join a shared list by code handler (PRD-0004).
   FR-1 through FR-14, NFR-1 through NFR-6."
  (:require [clojure.string :as str]
            [comprineas.lists.db :as db]
            [comprineas.lists.sse :as sse]
            [selmer.parser :as selmer]
            [ring.util.response :as resp]))

;; ── In-Memory Rate Limiting (FR-9, NFR-2, NFR-3) ──

(def ^:private join-rate-limit-state (atom {}))

(def ^:private rate-limit-window-ms 60000)   ; 60 seconds
(def ^:private max-attempts 10)

(defn- now-ms []
  (System/currentTimeMillis))

(defn- clean-attempts [attempts]
  (let [cutoff (- (now-ms) rate-limit-window-ms)]
    (filterv #(> % cutoff) attempts)))

(defn- record-attempt! [user-id]
  (swap! join-rate-limit-state update user-id
         (fn [attempts]
           (conj (clean-attempts (or attempts [])) (now-ms)))))

(defn- within-limit? [user-id]
  (< (count (clean-attempts (get @join-rate-limit-state user-id [])))
     max-attempts))

;; ── Code validation & normalization (FR-2, NFR-6) ──

(defn- normalize-code [raw]
  (-> raw (str/trim) (str/lower-case)))

(defn- valid-code? [code]
  (boolean (re-matches #"[a-z0-9]{6}" code)))

;; ── Error response helpers ──

(defn- join-error-response [msg status]
  (-> (selmer/render-file "lists/join-form.html" {:error msg})
      (resp/response)
      (resp/content-type "text/html")
      (resp/status status)))

;; ── POST /join handler ──

(defn join-list!
  "POST /join — Join a shared list by code.
   Processing order: trim → lowercase → validate → lookup (FR-2)."
  [{:keys [db current-user] :as req}]
  (let [user-id  (:id current-user)
        raw-code (get-in req [:params :code])]
    ;; Not logged in → redirect to login (FR-1 implied: form is on dashboard)
    (if (nil? user-id)
      (resp/redirect "/login" :see-other)
      (do
        ;; Count every POST regardless of outcome (Q5)
        (record-attempt! user-id)
        (cond
          ;; Rate limit exceeded (FR-9)
          (not (within-limit? user-id))
          (join-error-response
            "Too many attempts. Please try again in one minute." 429)

          :else
          (let [code (normalize-code raw-code)]
            (if-not (valid-code? code)
              ;; Invalid format (FR-2)
              (join-error-response
                "Code must be 6 alphanumeric characters." 400)
              ;; Lookup list by code (FR-3)
              (if-let [list (db/find-by-code db code)]
                (cond
                  ;; Completed list (FR-5)
                  (= (:status list) "completed")
                  (join-error-response
                    "This list has been completed and is no longer accepting new members."
                    400)

                  ;; Deleted lists are treated as not found via the same query,
                  ;; but if a row ever slips through with status='deleted',
                  ;; treat it as not found per FR-4 / Q3.
                  (= (:status list) "deleted")
                  (join-error-response
                    "No list found with this code." 400)

                  ;; Already participant → redirect (FR-6, FR-7)
                  (db/is-participant? db (:id list) user-id)
                  (resp/redirect (str "/list/" code) :see-other)

                  ;; New participant → insert, broadcast, redirect (FR-8, FR-10)
                  :else
                  (do
                    (try
                      (db/add-participant! db (:id list) user-id)
                      (sse/broadcast! db code "participant-joined"
                                      {:list_id (:id list)
                                       :user_id user-id})
                      (catch Exception e
                        ;; Race-condition guard: UNIQUE violation is benign (FR-8, NFR-4)
                        (when-not (str/includes?
                                    (str/lower-case (ex-message e))
                                    "unique constraint")
                          (throw e))))
                    (resp/redirect (str "/list/" code) :see-other)))

                ;; List not found (FR-4, Q3: same error for deleted codes)
                (join-error-response
                  "No list found with this code." 400)))))))))
