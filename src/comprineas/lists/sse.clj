(ns comprineas.lists.sse
  "Server-Sent Events infrastructure for real-time list updates (PRD-0003 FR-10, PRD-0004 FR-10, PRD-0005 FR-9/10/11, PRD-0006 FR-8).
   Uses an atom to track SSE channels per list code.
   Broadcasts typed events (list-updated, participant-joined, item-added, item-updated, item-deleted, list-created) to connected clients."
  (:require [cheshire.core :as json]
            [ring.util.response :as response]))

;; ──────────────────────────────────────────────────────────
;; Channel registry
;; ──────────────────────────────────────────────────────────

(defonce ^:private sse-channels (atom {}))

(defn register-channel!
  "Register an SSE channel for a list code. Returns the channel for convenience."
  [list-code channel]
  (swap! sse-channels update list-code (fnil conj #{}) channel)
  channel)

(defn unregister-channel!
  "Remove an SSE channel for a list code."
  [list-code channel]
  (swap! sse-channels update list-code (fn [chs]
                                         (let [remaining (disj chs channel)]
                                           (if (empty? remaining) nil remaining)))))

(defn connected-viewers
  "Return the set of SSE channels for a list code."
  [list-code]
  (get @sse-channels list-code #{}))

;; ──────────────────────────────────────────────────────────
;; send! helper — delegates to http-kit send! when available
;; ──────────────────────────────────────────────────────────
;; Broadcast
;; ──────────────────────────────────────────────────────────

(defn broadcast!
  "Broadcast an SSE event to all connected clients for a list.
   event-type: e.g. 'item-added', 'item-updated', 'item-deleted', 'participant-joined', 'list-updated', 'list-created'
   data: a map that will be JSON-encoded"
  ([ds list-code event-type data]
   (let [channels (connected-viewers list-code)
         payload (json/generate-string (merge {:type event-type} data))
         sse-data (str "event: " event-type "\ndata: " payload "\n\n")]
     (doseq [ch channels]
       (try
         (send! ch sse-data)
         (catch Exception _
           ;; Channel likely closed; remove it
           (unregister-channel! list-code ch)))))))

(defn broadcast-dashboard!
  "Broadcast an SSE event to all dashboard-connected clients.
   Used for list-created events (PRD-0006 FR-8)."
  ([ds event-type data]
   (broadcast! ds "dashboard" event-type data)))

;; ──────────────────────────────────────────────────────────
;; SSE handlers
;; ──────────────────────────────────────────────────────────

(defn sse-handler
  "Ring handler for SSE connections at /list/:code/events.
   Sets up the SSE stream and registers the channel.
   Requires http-kit server (async response)."
  [req]
  (let [list-code (get-in req [:path-params :code])
        ;; http-kit async response
        on-open (fn [ch]
                  (register-channel! list-code ch)
                  ;; Send initial comment to keep connection alive
                  (send! ch ":ok\n\n"))
        on-close (fn [ch]
                   (unregister-channel! list-code ch))]
    ;; Return async response for http-kit
    {:status 200
     :headers {"Content-Type" "text/event-stream"
               "Cache-Control" "no-cache"
               "Connection" "keep-alive"
               "X-Accel-Buffering" "no"}
     :body (fn [ch]
             (on-open ch))}))

(defn dashboard-sse-handler
  "Ring handler for dashboard SSE connections at /dashboard/events.
   Registers channel under the 'dashboard' key."
  [req]
  (let [on-open (fn [ch]
                  (register-channel! "dashboard" ch)
                  (send! ch ":ok\n\n"))
        on-close (fn [ch]
                   (unregister-channel! "dashboard" ch))]
    {:status 200
     :headers {"Content-Type" "text/event-stream"
               "Cache-Control" "no-cache"
               "Connection" "keep-alive"
               "X-Accel-Buffering" "no"}
     :body (fn [ch]
             (on-open ch))}))

;; send! helper — delegates to http-kit send! when available
(defn send!
  "Send data to an http-kit channel. Wraps org.httpkit.server/send!."
  [ch data]
  (try
    ((resolve 'org.httpkit.server/send!) ch data)
    (catch Exception e
      ;; http-kit not available in test mode; no-op
      nil)))