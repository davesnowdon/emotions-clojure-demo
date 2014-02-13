(ns emotions-demo.main
  (:require [clojure.core.async :as async
             :refer [<! >! <!! timeout chan alt! put! go go-loop]]
            [emotions-demo.core :refer :all]))

(defn -main
  [& args]
  (run-demo (first args)))
