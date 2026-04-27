(ns comprineas.lists.sse
  "Server-Sent Events infrastructure for real-time list updates."
  (:require [clojure.string :as string]
            [org.httpkit.server :as http-kit]))

;; ──────────────────────────────────────────────────────────
;; Channel registry
;; ──────────────────────────────────────────────────────────

(defonce ^:private sse-channels (atom {}))

(defn register-channel!
  "Register an SSE channel for a list code."
  [list-code channel]
  (swap! sse-channels update list-code (fnil conj #{}) channel)
  channel)

(defn unregister-channel!
  "Remove an SSE channel for a list code."
  [list-code channel]
  (swap! sse-channels update list-code
         (fn [chs]
           (let [remaining (disj chs channel)]
             (if (empty? remaining) nil remaining)))))

(defn connected-viewers
  "Return the set of SSE channels for a list code."
  [list-code]
  (get @sse-channels list-code #{}))

;; ──────────────────────────────────────────────────────────
;; SSE streaming helpers
;; ──────────────────────────────────────────────────────────

(def ^:private sse-headers
  {"Content-Type"      "text/event-stream"
   "Cache-Control"     "no-cache"
   "Connection"        "keep-alive"
   "X-Accel-Buffering" "no"})

(defn- stream!
  "Send data on an http-kit channel without closing it.
   Each call includes the full response map per http-kit SSE convention."
  [ch data]
  (http-kit/send! ch data false))

;; ──────────────────────────────────────────────────────────
;; Broadcast
;; ──────────────────────────────────────────────────────────

(defn broadcast!
  "Broadcast an SSE event to all connected clients for a list."
  [ds list-code event-type data]
  (let [channels (connected-viewers list-code)
        sse-data (if-let [html (:html data)]
                   (let [lines (string/split-lines html)]
                     (str "event: " event-type "\n"
                          (string/join "\n" (map #(str "data: " %) lines))
                          "\n\n"))
                   (str "event: " event-type "\ndata: {}\n\n"))]
    (doseq [ch channels]
      (try
        (stream! ch {:status 200, :headers sse-headers, :body sse-data})
        (catch Exception _
          (unregister-channel! list-code ch))))))

(defn broadcast-dashboard!
  "Broadcast an SSE event to all dashboard-connected clients."
  [ds event-type data]
  (broadcast! ds "dashboard" event-type data))

;; ──────────────────────────────────────────────────────────
;; SSE handlers — must NOT go through wrap-defaults
;; ──────────────────────────────────────────────────────────

(defn sse-handler
  "Ring handler for SSE connections at /list/:code/events."
  [req]
  (let [list-code (get-in req [:path-params :code])]
    (http-kit/as-channel req
                         {:on-open
                          (fn [ch]
                            (register-channel! list-code ch)
                            (.println System/err (str "[SSE] Registered for list:" list-code " total:" (count (connected-viewers list-code))))
                            (.flush System/err)
         ;; Send initial SSE comment per the SSE spec
                            (stream! ch {:status 200, :headers sse-headers, :body ":ok\n\n"}))
                          :on-close
                          (fn [ch _status]
                            (unregister-channel! list-code ch))})))

(defn dashboard-sse-handler
  "Ring handler for dashboard SSE connections at /dashboard/events."
  [req]
  (http-kit/as-channel req
                       {:on-open
                        (fn [ch]
                          (register-channel! "dashboard" ch)
                          (.println System/err (str "[SSE] Registered dashboard channel, total:" (count (connected-viewers "dashboard"))))
                          (.flush System/err)
                          (stream! ch {:status 200, :headers sse-headers, :body ":ok\n\n"}))
                        :on-close
                        (fn [ch _status]
                          (unregister-channel! "dashboard" ch))}))