(ns app.compute
  (:require [instaparse.core :as insta]
            [fipp.edn :refer [pprint]]
            [clojure.walk :refer [prewalk postwalk]]
            [app.util :as util]
            [cljs.reader :refer [read-string]]))

(defonce memoized-parser (util/memoize-last-val insta/parser))

(defn- string-result
  [result & {:keys [error]}]
  [:div {:style {:padding     10
                 :color       (if error "#D53182" "inherit")
                 :font-family "monospace"
                 :white-space "pre-wrap"}}
   result])

(defn- vec->element [v]
  [:div
   {:class (str "parse-tag t-" (name (first v)))}
   [:span
    {:class "tag-name"}
    (str (first v) " ")] (rest v)])

(defn- visualized-result [result]
  (let [#_result #_(prewalk #(if (instaparse.auto-flatten-seq/afs? %)  (seq %) %) result)
        result (postwalk
                 (fn [x]
                   (if (vector? x) (vec->element x) x))
                 result)]
    [:div {:class "parse-output"} (interpose [:div {:class "parse-sep"}] result)]))

(defn parse
  ([grammar sample options] (parse {:grammar grammar :sample sample :options options}))
  ([{:keys [grammar sample options]}]
             (try
               (let [options (read-string options)
                     parser (apply memoized-parser
                                   (apply concat [grammar] (seq (select-keys options [:input-format :output-format :string-ci]))))
                     result (apply insta/parses
                                   (apply concat [parser sample] (seq (select-keys options [:start :partial :total :unhide :trace]))))
                     failure (if (insta/failure? result) (insta/get-failure result) nil)
                     output-format (:output-format options)
                     max-parses (or (:max-parses options) 20)]
                 (cond failure (string-result (pr-str failure) :error true)
                       (some #{:hiccup :enlive} #{output-format}) (string-result (with-out-str (pprint result)))
                       :else (visualized-result (take max-parses result))))
               (catch :default e
                 (.log js/console e)
                 (let [message (if (string? e) e (str e))]
                   (string-result message :error true))))))