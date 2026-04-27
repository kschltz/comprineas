(ns comprineas.lists.sse
  "Server-Sent Events infrastructure for real-time list updates (PRD-0003 FR-10, PRD-0004 FR-10, PRD-0005 FR-9/10/11, PRD-0006 FR-8).
   Uses an atom to track SSE channels per list code.
   Broadcasts typed events (list-updated, participant-joined, item-added, item-updated, item-deleted, list-created) to connected clients."
  (:require [clojure.string :as string]
            [org.httpkit.server :as http-kit]))

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
;; SSE streaming helpers
;; ──────────────────────────────────────────────────────────

(def ^:private sse-response-headers
  "Standard headers for SSE handshake response."
  {"Content-Type"      "text/event-stream"
   "Cache-Control"     "no-cache"
   "Connection"        "keep-alive"
   "X-Accel-Buffering" "no"})

(defn- send-sse!
  "Send data on an http-kit SSE channel without closing it.
   For the initial response, pass a response map; for subsequent
   SSE events, pass a string."
  ([ch data]
   ;; false = don't close the channel after sending (keep it open for streaming)
   (http-kit/send! ch data false)))

;; ──────────────────────────────────────────────────────────
;; Broadcast
;; ──────────────────────────────────────────────────────────

(defn broadcast!
  "Broadcast an SSE event to all connected clients for a list.
   event-type: e.g. 'item-added', 'item-updated', 'item-deleted', 'participant-joined', 'list-updated', 'list-created'
   data: a map that may contain :html for direct swap, or other keys to send as SSE data."
  ([ds list-code event-type data]
   (let [channels (connected-viewers list-code)
         ;; HTMX SSE swap expects raw HTML in the data field,
         ;; not JSON.  For hx-trigger="sse:event-name" the data
         ;; content doesn't matter — only the event name does.
         ;; SSE multi-line data: each line prefixed with "data: "
         sse-data (if-let [html (:html data)]
                    (let [lines (string/split-lines html)]
                      (str "event: " event-type "\n"
                           (string/join "\n" (map #(str "data: " %) lines))
                           "\n\n"))
                    (str "event: " event-type "\ndata: {}\n\n"))]
     (doseq [ch channels]
       (try
         (send-sse! ch sse-data)
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
   Uses http-kit's as-channel to keep the connection open for streaming events."
  [req]
  (let [list-code (get-in req [:path-params :code])
        async-ch  (:async-channel req)]
    (binding [*out* *err*] (println "[SSE] sse-handler called for list:" list-code "async-channel:" (some? async-ch)))
    (if-not async-ch
      {:status 200 :headers sse-response-headers :body ""}
      (http-kit/as-channel req
                           {:on-open   (fn [ch])
                    ;; Send initial HTTP response to establish SSE connection
                    ;; false = keep channel open for streaming
                            (http-kit/send! ch {:status  200
                                                :headers sse-response-headers
                                                :body    ""}
                                            false)
                            (register-channel! list-code ch)
                            (binding [*out* *err*] (println "[SSE] Registered channel for list:" list-code "total:" (count (connected-viewers list-code))))
                    ;; Send initial comment to keep connection alive
                            (send-sse! ch ":ok\n\n")}
                           :on-close  (fn [ch _status]
                                        (unregister-channel! list-code ch))))))

(defn dashboard-sse-handler
  "Ring handler for dashboard SSE connections at /dashboard/events.
   Uses http-kit's as-channel to keep the connection open for streaming events."
  [req]
  (http-kit/as-channel req
                       {:on-open   (fn [ch]
                  ;; Send initial HTTP response to establish SSE connection
                  ;; false = keep channel open for streaming
                                     (http-kit/send! ch {:status  200
                                                         :headers sse-response-headers
                                                         :body    ""}
                                                     false)
                                     (register-channel! "dashboard" ch)
                  ;; Send initial comment to keep connection alive
                                     (send-sse! ch ":ok\n\n"))
                        :on-close  (fn [ch _status]
                                     (unregister-channel! "dashboard" ch))}))