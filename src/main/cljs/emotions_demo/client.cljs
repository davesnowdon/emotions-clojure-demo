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
    {:motivations motivations :cur-state cur-state :history {}}
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

(defn valence-arousal [{:keys [data] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (let [valence (get-in data [:cur-state :valence])
            arousal (get-in data [:cur-state :arousal])]
        (dom/div #js {:className "valence_arousal"}
                 (dom/div nil
                          (dom/span nil "Valence")
                          (dom/span nil valence))
                 (dom/div nil
                          (dom/span nil "Arousal")
                          (dom/span nil arousal)))))))

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

(defn history [app owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "history"}
               (dom/span nil "history")))))


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
