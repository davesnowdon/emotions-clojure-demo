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
            [hiccup.page :refer [html5 include-js include-css]]
            [hiccup.element :refer [javascript-tag]]
            [emotions.serialise :refer [serialise]]
            [emotions-demo.core :refer :all]))

(defconfig my-config (io/resource "config/demo.edn"))

(def remote-clients-atom (atom {}))

; would prefer not to have these as globals, need to think of a
; cleaner way to share them with ws-handler
(def state-chan (chan))

(def percept-chan (chan))

(def next-id!
  (let [counter (java.util.concurrent.atomic.AtomicLong.)]
    (fn [] (.incrementAndGet counter))))

(defn add-client
  [clients-atom client-id listener-chan]
  (swap! clients-atom assoc client-id listener-chan))

(defn remote-state-emitter
  [state-chan clients-atom]
  (go-loop [state (<! state-chan)]
    (when state
      (let [msg (serialise state)]
        (doseq [[client-id client-chan] @clients-atom]
          (>! client-chan (pr-str msg)))
        (recur (<! state-chan))))))

(defn index-page []
  (html5
   [:head
    [:title "Clojure Emotional model demo"]]
    [:body
     [:div#app]
      (include-css "/css/demo.css")
      (include-js "/jslib/react/react-0.9.0.js") ; only required in dev build
      (include-js "/out/goog/base.js") ; only required in dev build
      (include-js "/jslib/d3/d3.v3.min.js")
      (include-js "/jslib/dimple/dimple.v1.1.4.min.js")
      (include-js "/js/demo.js")
      (javascript-tag "goog.require('emotions_demo.client');") ; only required in dev build
      ]))

(defn ws-handler [req]
  (with-channel req ws
    (println "Opened connection from" (:remote-addr req))
    (let [client-id (next-id!)]
      (add-client remote-clients-atom client-id ws))
    (go-loop []
      (when-let [{:keys [message]} (<! ws)]
        (println "Message received:" message)
        (let [[msg-type msg-body] (read-string message)]
          (condp = msg-type
            :connect (start-robot msg-body percept-chan state-chan)
            :recognised (recognised-agent msg-body percept-chan)
            (println "Unexpected message" msg-type msg-body)))
        (recur)))))

(defn app-routes []
  (routes
    (GET "/" [] (response (index-page)))
    (GET "/ws" [] ws-handler)
    (resources "/js" {:root "js"})
    (resources "/out" {:root "out"}) ; only required in dev build
    (resources "/" )
    ))

(defn webapp []
  (let [remote-state-chan (chan)]
    (add-state-listener internal-clients-atom remote-state-chan)
    (remote-state-emitter remote-state-chan remote-clients-atom)
    (start-emotions percept-chan state-chan)
    (-> (app-routes)
        api)))
