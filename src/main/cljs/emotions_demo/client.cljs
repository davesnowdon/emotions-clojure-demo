(ns emotions-demo.client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:refer-clojure :exclude [chars])
  (:require
   [cljs.core.async :refer [<! >! put! close! timeout]]
   [cljs.reader :as reader]
   [chord.client :refer [ws-ch]]
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]))

(enable-console-print!)


(defn message [{:keys [message]} owner]
  (om/component
    (dom/li nil message)))

(defn update-state
  [{:keys [history] :as old-state}
   {:keys [sv valence arousal percepts motivations]}]
  (let [cur-state (-> sv (assoc :valence valence) (assoc :arousal arousal))]
    {:motivations motivations
     :cur-state cur-state
     :history
     (reduce (fn [a k] (assoc a k (conj (k history []) (k cur-state)))) {} (keys cur-state))}
    ))

(defn bind-state [{:keys [ws data]}]
  (go-loop []
    (when-let [{update :message} (<! ws)]
      (prn update)
      (om/transact! data
                    (fn [cur-state]
                      (update-state cur-state
                                     (reader/read-string update))))
      (recur))))

(defn single-value [{:keys [data] :as c} owner
                    {:keys [label path] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [value (get-in data path)]
        (dom/div #js {:className "single_value"}
                 (dom/span #js {:className "label"} label)
                 (dom/span #js {:className "value"} value))))))

(defn valence-arousal [{:keys [data] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "valence_arousal"}
               (om/build single-value c
                         {:opts
                          {:label "Valence"
                           :path [:cur-state :valence]}})
               (om/build single-value c
                         {:opts
                          {:label "Arousal"
                           :path [:cur-state :arousal]}})))))

(defn satisfaction-vector [app owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "satisfaction_vector"}
               (dom/span nil "Satisfaction vector")))))

(defn motivations [app owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "motivations"}
               (dom/span nil "motivations")))))

(defn history [{:keys [data] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (let [history (:history data)]
        (dom/div #js {:className "history"}
                 (dom/span nil "history"))))))


(defn emotion-display-app [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (bind-state app))
    om/IRender
    (render [_]
      (dom/div #js {:className "emotions"}
               (dom/h1 nil "Emotional NAO")
               (dom/div #js {:className "current_state"}
                        (om/build valence-arousal app)
                        (om/build satisfaction-vector app))
               (om/build motivations app)
               (om/build history app)))))

(go
  (let [ws (<! (ws-ch "ws://localhost:3000/ws"))
        app-state (atom {:ws ws :data { :motivations [] :cur-state {} :history {}}})]
        (om/root app-state emotion-display-app
         (.getElementById js/document "app"))))
