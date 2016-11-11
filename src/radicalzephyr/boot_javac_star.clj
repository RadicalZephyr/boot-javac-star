(ns radicalzephyr.boot-javac-star
  {:boot/export-tasks true}
  (:require
    [boot.core :as core]
    [boot.pod  :as pod]
    [boot.util :as util]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.set :as set]
    [cljunit.core :refer [run-tests-in-classes]])
  (:import
   (java.io ByteArrayOutputStream)
   (java.util Arrays)
   (java.util.concurrent ConcurrentHashMap)
   (clojure.lang DynamicClassLoader)
   (javax.tools DiagnosticCollector
                Diagnostic$Kind
                ForwardingJavaFileManager
                JavaCompiler
                JavaFileObject$Kind
                SimpleJavaFileObject
                StandardJavaFileManager
                ToolProvider)))

(defn- construct-class-name [prefix path]
  (let [path-seq (-> path
                     (str/replace prefix "")
                     (str/replace #"\.java$" "")
                     (str/split #"/"))]
    (->> path-seq
         (remove empty?)
         (interpose ".")
         (apply str))))

(def ^ConcurrentHashMap class-cache
  (-> (.getDeclaredField clojure.lang.DynamicClassLoader "classCache")
      (doto (.setAccessible true))
      (.get nil)))

(defn- source-object
  [class-name source]
  (proxy [SimpleJavaFileObject]
      [(java.net.URI/create (str "string:///"
                                 (.replace ^String class-name \. \/)
                                 (. JavaFileObject$Kind/SOURCE extension)))
       JavaFileObject$Kind/SOURCE]
    (getCharContent [_] source)))

(defn- class-object
  "Returns a JavaFileObject to store a class file's bytecode."
  [class-name baos]
  (proxy [SimpleJavaFileObject]
      [(java.net.URI/create (str "string:///"
                                 (.replace ^String class-name \. \/)
                                 (. JavaFileObject$Kind/CLASS extension)))
       JavaFileObject$Kind/CLASS]
    (openOutputStream [] baos)))

(defn- class-manager
  [cl manager cache]
  (proxy [ForwardingJavaFileManager] [manager]
    (getClassLoader [location]
      cl)
    (getJavaFileForOutput [location class-name kind sibling]
      (.remove class-cache class-name)
      (class-object class-name
                    (-> cache
                        (swap! assoc class-name (ByteArrayOutputStream.))
                        (get class-name))))))

(defn- source->bytecode [opts diag-coll class-name source]
  (let [compiler   (or (ToolProvider/getSystemJavaCompiler)
                       (throw (Exception. "The java compiler is not working. Please make sure you use a JDK!")))
        cache    (atom {})
        mgr      (class-manager nil (.getStandardFileManager compiler nil nil nil) cache)
        task     (.getTask compiler nil mgr diag-coll opts nil [(source-object class-name source)])]
    (when (.call task)
      (zipmap
       (keys @cache)
       (->> @cache
            vals
            (map #(.toByteArray ^ByteArrayOutputStream %)))))))

(defn- compile-java
  [opts diag-coll class-name source]
  (let [cl              (clojure.lang.RT/makeClassLoader)
        class->bytecode (source->bytecode opts diag-coll class-name source)]
    (doseq [[class-name bytecode] class->bytecode]
      (.defineClass ^DynamicClassLoader cl class-name bytecode nil))
    (keys class->bytecode)))

(core/deftask javac*
  "Compile java sources."
  [o options OPTIONS [str] "List of options passed to the java compiler."]
  (let [tgt (core/tmp-dir!)]
    (core/with-pre-wrap [fs]
      (let [throw?    (atom nil)
            diag-coll (DiagnosticCollector.)
            opts      (->> ["-d"  (.getPath tgt)
                            "-cp" (System/getProperty "boot.class.path")]
                           (concat options)
                           (into-array String) Arrays/asList)
            handler   {Diagnostic$Kind/ERROR util/fail
                       Diagnostic$Kind/WARNING util/warn
                       Diagnostic$Kind/MANDATORY_WARNING util/warn}
            tmp-srcs  (some->> (core/input-files fs)
                               (core/by-ext [".java"]))]
        (when (seq tmp-srcs)
          (util/info "Compiling %d Java source files...\n" (count tmp-srcs))
          (doseq [tmp-src tmp-srcs]
            (let [prefix (str (core/tmp-dir tmp-src))
                  file (core/tmp-file tmp-src)
                  class-name (construct-class-name prefix (str file))]
              (compile-java opts diag-coll class-name (slurp file))))
          (doseq [d (.getDiagnostics diag-coll) :let [k (.getKind d)]]
            (when (= Diagnostic$Kind/ERROR k) (reset! throw? true))
            (let [log (handler k util/info)]
              (if (nil? (.getSource d))
                (log "%s: %s\n"
                     (.toString k)
                     (.getMessage d nil))
                (log "%s: %s, line %d: %s\n"
                     (.toString k)
                     (.. d getSource getName)
                     (.getLineNumber d)
                     (.getMessage d nil)))))
          (when @throw? (throw (Exception. "java compiler error")))))
      (-> fs (core/add-resource tgt) core/commit!))))
