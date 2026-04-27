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
   First call should include {:status :headers :body} map.
   Subsequent calls can send raw SSE data strings."
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
        (stream! ch sse-data)
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
  (let [list-code (get-in req [:path-params :code])
        has-async? (some? (:async-channel req))]
    (when (or (nil? list-code) (not has-async?))
      (binding [*out* *err*]
        (println "[SSE WARN] sse-handler called with:" {:list-code list-code :async-channel has-async? :uri (:uri req) :method (:request-method req)})))
    (http-kit/as-channel req
                         {:on-open
                          (fn [ch]
         ;; Send the initial HTTP 200 response to establish SSE
                            (stream! ch {:status 200, :headers sse-headers, :body ""})
                            (register-channel! list-code ch)
         ;; Initial SSE keep-alive comment
                            (stream! ch ":ok\n\n"))
                          :on-close
                          (fn [ch _status]
                            (unregister-channel! list-code ch))})))

(defn dashboard-sse-handler
  "Ring handler for dashboard SSE connections at /dashboard/events."
  [req]
  (http-kit/as-channel req
                       {:on-open
                        (fn [ch]
                          (stream! ch {:status 200, :headers sse-headers, :body ""})
                          (register-channel! "dashboard" ch)
                          (stream! ch ":ok\n\n"))
                        :on-close
                        (fn [ch _status]
                          (unregister-channel! "dashboard" ch))}))