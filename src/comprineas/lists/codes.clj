(ns comprineas.lists.codes
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]))

(def ^:private code-chars "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

(defn- random-code []
  (str/join (repeatedly 6 #(rand-nth code-chars))))

(defn generate-unique-code
  "Generate a unique 6-character share code.
   Retries up to 3 times on collision; the UNIQUE constraint
   on lists.code is the final safety net."
  [ds]
  (loop [attempts 0]
    (let [code (random-code)
          existing (jdbc/execute-one! ds ["SELECT 1 AS one FROM lists WHERE code = ?" code])]
      (if (nil? existing)
        code
        (if (>= attempts 3)
          code
          (recur (inc attempts)))))))
