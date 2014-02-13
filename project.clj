(defproject emotions-demo "0.1.0"
  :description "Demonstrate use of core.aync and emotions framework with NAO robot"
  :url "https://github.com/davesnowdon/emotions-clojure-demo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :main emotions-demo.core

  :source-paths ["src" "src/main/clj"]
  :test-paths ["test" "src/test/clj"]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [emotions "0.1.3"]
                 [naojure "0.2.2"]

                 [org.clojure/clojurescript "0.0-2156"]
                 [org.clojure/tools.reader "0.8.3"]
                 [prismatic/dommy "0.1.2"]
                 [om "0.3.6"]]
  :plugins [[lein-pdo "0.1.1"]
            [lein-cljsbuild "1.0.2"]
            [jarohen/lein-frodo "0.2.11"]]

  :frodo/config-resource "config/demo.edn"

  :aliases {"dev" ["pdo" "cljsbuild" "auto" "dev," "frodo"]
            "prod" ["pdo" "cljsbuild" "once" "release," "frodo"]}

  :resource-paths ["resources" "target/resources"]

  :cljsbuild {
     :builds [{:id "dev"
               :source-paths ["src/main/cljs"]
               :compiler {
                 :output-to "target/resources/js/demo.js"
                 :output-dir "target/resources/out"
                 :optimizations :none
                 :source-map true}}

              {:id "release"
               :source-paths ["src/cljs"]
               :compiler {
                :output-to "target/resources/js/demo.js"
                :optimizations :advanced
                :pretty-print false
                :preamble ["react/react.min.js"]
                :externs ["react/externs/react.js"]}}
                ]}

)
