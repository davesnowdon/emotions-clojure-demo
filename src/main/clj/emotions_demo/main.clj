(ns emotions-demo.main
  (:require [clojure.core.async :as async
             :refer [<! >! <!! timeout chan alt! put! go go-loop]]
            [nomad :refer [defconfig]]
            [clojure.java.io :as io]
            [emotions-demo.core :refer :all]))

(defconfig my-config (io/resource "config/demo.edn"))

(defn -main
  [& args]
  (if-let [hostname (first args)]
    (run-demo hostname)
    (run-demo (:robot-hostname (my-config)))))
