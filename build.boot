(set-env! :resource-paths #{"resources" "src"}
          :source-paths   #{"test"}
          :dependencies   '[[org.clojure/clojure "RELEASE" :scope "provided"]
                            [boot/core "2.6.0" :scope "provided"]
                            [adzerk/boot-test "RELEASE" :scope "test"]
                            [radicalzephyr/bootlaces "0.1.15-SNAPSHOT" :scope "test"]])

(def +version+ "0.1.0-SNAPSHOT")

(bootlaces! +version+)

(task-options!
 pom {:project     'radicalzephyr/boot-javac*
      :version     +version+
      :description "Compile java classes with reloading support"
      :url         "https://github.com/radicalzephyr/boot-javac*"
      :scm         {:url "https://github.com/radicalzephyr/boot-javac*"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(require '[adzerk.boot-test :refer [test]])
