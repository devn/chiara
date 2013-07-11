(ns clarity.syntax
  (:refer-clojure :exclude [peek])
  (use [clarity.reader macros utils]
        clarity.utils))

(defn ^:private current-ns
  "Return the name of the current
  ns as a symbol."
  [] (ns-name *ns*))

(defn ^:private read-literal
  "Reads chars into a string (verbatim) until
  the next unmatched ')'."
  [reader]
  (loop [chars []
         brackets 0]
    (let [char (read-1 reader)]
      (cond
        (not char)
          (read reader)
        (= "(" (str char))
          (recur (conj chars char) (inc brackets))
        (= ")" (str char))
          (if (zero? brackets)
            (apply str chars)
            (recur (conj chars char) (dec brackets)))
        :else
          (recur (conj chars char) brackets)))))

(defonce ^:private symbol-table (atom {}))

(defn ^:private symbol-dispatch
  "Called by the reader on encountering a
  list - decides whether to read as a macro
  or as a regular list."
  [reader _]
  (if (= ")" (str (peek reader)))
    (do (read-1 reader) ())
    (let [first (read-next reader)]
      (if-let [f (get-in @symbol-table [(current-ns) first])]
        (do
          (if (#{\space \newline} (peek reader)) (read-1 reader))
          (f (read-literal reader)))
        (conj (read-delimited-list \) reader) first)))))

(defn use-symbol-macro [{:keys [symbol reader]}]
  (swap! symbol-table assoc-in [(current-ns) symbol] reader)
  (use-reader-macro {:char \( :reader symbol-dispatch}))

(defn use-symbol-macros [& args]
  (dorun (map use-symbol-macro args)))

(defn use-syntax
  "Takes one or more 'symbol macros' of
  the form `{:symbol :reader}`, and enables
  them for the current namespace."
  [& args]
  (apply use-symbol-macros args))

(defnrecord SyntaxMacro [symbol reader]
  (fn [this & []]
    (throw
      (Exception.
        (str (.symbol this) " is a syntax macro "
             "and can't be called as a function. "
             "Please make sure you have enabled it "
             "with (use-syntax " (.symbol this) ").")))))

(defmacro defsyntax
  "Creates a macro which operates on a string of
  it contents. Must be explicitly enabled, per-namespace,
  with `use-syntax`.
  e.g.
    (defsyntax r [s] `(def ~'my-string ~(.toUpperCase s)))
    (use-syntax r)
    (r hi there)
    (println my-string)
  ;=> HI THERE

  Note: Parentheses '()' MUST be balanced within the body
  of the macro, although other brackets '{}[]' don't
  matter."
  [symbol & rest]
 `(def ~symbol (SyntaxMacro. '~symbol (fn ~@rest))))