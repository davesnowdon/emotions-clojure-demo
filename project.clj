(defproject emotions-demo "0.1.0-SNAPSHOT"
  :description "Demonstrate use of core.aync and emotions framework with NAO robot"
  :url "https://github.com/davesnowdon/emotions-clojure-demo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src" "src/main/clojure"]
  :test-paths ["test" "src/test/clojure"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [emotions "0.1.2"]
                 [naojure "0.2.1"]]
  :main emotions-demo.core)
