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

(def imgroot "/img")
(def single-value-img-size 200)

;(defn format-float [f ndp]
;  (str (* nd)))

(defn- motivations->id+name [motivations]
  (reduce (fn [a m] (assoc a (:id m) (:name m))) {} motivations))

(defn- sv->values [sv motivations]
  (let [ids (map :id motivations)
        mnames (motivations->id+name motivations)]
    (map (fn [id] {:id id :name (mnames id) :value (sv id)}) ids)))

(defn add-history [history new-state]
  (reduce (fn [a k] (assoc a k (conj (k history []) (k new-state)))) {}
          (keys new-state)))

; this seems to change the state to not be a cursor
(defn make-new-state
  [{:keys [ws sv-history va-history] :as old-state}
   {:keys [sv valence arousal percepts motivations]}]
  {:motivations motivations
   :sv (sv->values sv motivations)
   :va [{:id :valence :name "Valence" :value valence}
        {:id :arousal :name "Arousal" :value arousal}]
   :percepts percepts
   :sv-history (add-history sv-history sv)
   :va-history (add-history va-history {:valence valence :arousal arousal})}
    )

(defn bind-state [{:keys [ws] :as data}]
  (go-loop []
    (when-let [{update :message} (<! ws)]
      (prn update)
      (om/transact! data :data #(make-new-state % (reader/read-string update)))
      (recur))))

(defn single-value [{:keys [id name value] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (let [layout (:layout opts :vertical)
            layout-class (if (= layout :vertical) "vertical" "horizontal")
            only-positive (:only-positive opts true)
            bar-length (int (* (Math/abs value) single-value-img-size))
            bar-width 20
            imgsrc (if (< value 0) "red.png" "blue.png")
            width (if (= layout :vertical) bar-width bar-length)
            height (if (= layout :vertical) bar-length bar-width)]
        (dom/div #js {:className (str "single_value" " " layout-class)}
                 (dom/span #js {:className "label"} name)
                 (dom/span #js {:className "value"} value)
                 (dom/img #js {:src (str imgroot "/" imgsrc)
                               :width (str width)
                               :height (str height)}))))))

(defn valence-arousal [{:keys [va] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (apply dom/div #js {:className "valence_arousal"}
             (om/build-all single-value va {:key :id
                                            :opts {:layout :horizontal
                                                   :only-positive false}})))))


(defn satisfaction-vector [{:keys [sv] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "satisfaction_vector"}
               (dom/h2 nil "Satisfaction vector")
               (apply dom/div #js {:className "contents"}
                        (om/build-all single-value sv {:key :id
                                                       :opts {:layout :vertical
                                                              :only-positive true}})
                        )))))

(defn motivations [app owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "motivations"}
               (dom/span nil "motivations")))))

(defn history [{:keys [sv-history] :as c} owner opts]
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
                        (om/build valence-arousal (:data app))
                        (om/build satisfaction-vector (:data app)))
               (om/build motivations app)
               (om/build history (:data app))
      ))))

(go
  (let [ws (<! (ws-ch "ws://localhost:3000/ws"))
        app-state (atom {:ws ws :data {:motivations [] :sv [] :va []
                                       :percepts [] :sv-history {} :va-history {}} })]
    (om/root emotion-display-app app-state
             {:target (.getElementById js/document "app")})))
