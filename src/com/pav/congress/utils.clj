(ns com.pav.congress.utils
  "General utility functions."
  (:require [clojure.string :as s]
            [clojure.pprint :refer [pprint]])
  (:import java.util.Collections))

(defn- unkeywordize
  "Convert keyword to environment-like variable."
  [k]
  (if (keyword? k)
    (-> k
        name
        (s/replace "-" "_")
        s/upper-case)
    (str k)))

(defn- alter-env!
  "Alter environment map on particular class, by using environment key."
  [klass env-key newenv]
  (let [field (.getDeclaredField klass "theEnvironment")]
    (.setAccessible field true)
    (doto (.get field nil)
      (.clear)
      (.putAll newenv))))

(defn set-env!
  "Alter environment variables for current running JVM process, in runtime.
Useful for setting custom stuff not known at startup time. For me, dynamically
changing AWS keys from REPL or from code."
  ([mp]
     (let [;; make it PersistentHashMap, since System/getenv will return
           ;; UnmodifiableMap which can't be merged
           env     (into {} (System/getenv))
           newenv  (merge env
                          (reduce-kv (fn [m k v]
                                       (assoc m (unkeywordize k) v))
                                  {}
                                  mp))]
       ;(clojure.pprint/pprint newenv)
       (alter-env! (Class/forName "java.lang.ProcessEnvironment")
                   "theEnvironment"
                   newenv)))
  ([k v] (set-env! {k v})))

(defn pprint-str
  "Pretty print to string."
  [obj]
  (with-out-str
    (pprint obj)))
