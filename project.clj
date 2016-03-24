(defproject emotions-demo "0.2.0"
  :description "Demonstrate use of core.aync and emotions framework with NAO robot"
  :url "https://github.com/davesnowdon/emotions-clojure-demo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :main emotions-demo.main

  :source-paths ["src" "src/main/clj"]
  :test-paths ["test" "src/test/clj"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.5.0"]
                 [jarohen/chord "0.7.0"]
                 [jarohen/nomad "0.7.2"]
                 [ring/ring-core "1.4.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [org.clojure/core.async "0.2.374"]
                 [hiccup "1.0.5"]
                 [emotions "0.2.1"]
                 ;[naojure "0.2.4"]
                 [com.aldebaran/java-naoqi "2.1.4"]

                 [org.clojure/clojurescript "1.8.34"]
                 [org.clojure/tools.reader "0.10.0"]
                 [prismatic/dommy "1.1.0"]
                 [om "0.7.3"]]

  :plugins [[lein-pdo "0.1.1"]
            [lein-cljsbuild "1.1.3"]
            [jarohen/lein-frodo "0.4.2"]]

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
