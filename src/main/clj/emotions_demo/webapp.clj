(ns emotions-demo.webapp
  (:require [clojure.core.async :as async
             :refer [<! >! <!! timeout chan alt! put! go go-loop]]
            [ring.util.response :refer [response]]
            [chord.http-kit :refer [with-channel]]
            [nomad :refer [defconfig]]
            [clojure.java.io :as io]
            [compojure.core :refer [defroutes GET routes]]
            [compojure.handler :refer [api]]
            [compojure.route :refer [resources]]
            [hiccup.page :refer [html5 include-js]]
            [hiccup.element :refer [javascript-tag]]
            [emotions-demo.core :refer :all]))

(defconfig my-config (io/resource "config/demo.edn"))

(def remote-clients-atom (atom []))

(defn remote-state-emitter
  [state-chan clients-atom]
  (go-loop [state (<! state-chan)]
           (when state
             (doseq [client-chan @clients-atom]
               (>! client-chan (pr-str state)))
             (recur (<! state-chan)))))

(defn index-page []
  (html5
   [:head
    [:title "Clojure Emotional model demo"]]
    [:body
      [:div#app]
      (include-js "//fb.me/react-0.8.0.js") ; only required in dev build
      (include-js "/out/goog/base.js") ; only required in dev build
      (include-js "/js/demo.js")
      (javascript-tag "goog.require('emotions_demo.client');") ; only required in dev build
      ]))

(defn ws-handler [req]
  (with-channel req ws
    (println "Opened connection from" (:remote-addr req))
    (go-loop []
      (when-let [{:keys [message]} (<! ws)]
        (println "Message received:" message)
        (>! ws (format "You said: '%s' at %s." message (java.util.Date.)))
        (recur)))))

(defn app-routes []
  (routes
    (GET "/" [] (response (index-page)))
    (GET "/ws" [] ws-handler)
    (resources "/js" {:root "js"})
    (resources "/out" {:root "out"}) ; only required in dev build
    ))

(defn webapp []
  (let [remote-state-chan (chan)]
    (add-state-listener internal-clients-atom remote-state-chan)
    (remote-state-emitter remote-state-chan remote-clients-atom)
    (-> (app-routes)
        api)))
