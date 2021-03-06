(ns emotions-demo.client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:refer-clojure :exclude [chars])
  (:require
   [cljs.core.async :refer [<! >! put! close! timeout]]
   [cljs.reader :as reader]
   [chord.client :refer [ws-ch]]
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   ))

(enable-console-print!)

(def imgroot "/img")

(def bar-view-img-length 200)
(def bar-view-img-width 20)
(def bar-view-ndp 4)

(defn format-float
  "Simple minded function to truncate decimals"
  [f ndp]
  (let [s (str f)
        l (+ (.indexOf s ".") ndp)]
    (.substring s 0 l)))

(defn format-kw
  [k]
  (.substring (str k) 1))

(defn format-sv
  [sv]
  (reduce (fn [s [k v]] (str s " " (format-kw k) "=" (format-float v 4))) "" (sort sv)))

(defn format-date
  [date]
  (str date))

(defn format-agents
  [agents]
  (str agents))

(defn format-locations
  [locations]
  (str locations))

(defn- motivations->id+name [motivations]
  (reduce (fn [a m] (assoc a (:id m) (:name m))) {} motivations))

(defn- sv->values [sv motivations]
  (let [ids (map :id motivations)
        mnames (motivations->id+name motivations)]
    (map (fn [id] {:id id :name (mnames id) :value (sv id)}) ids)))

(defn add-history [history new-state]
  (let [timestamp (js/Date.)]
    (reduce (fn [h k] (conj h {:motivation k :timestamp timestamp :value (k new-state)}))
            history (keys new-state))))

(defn make-new-state
  [{:keys [ws sv-history va-history] :as old-state}
   {:keys [sv valence arousal percepts motivations stm ltm]}]
  {:motivations motivations
   :sv (sv->values sv motivations)
   :va [{:id :valence :name "Valence" :value valence}
        {:id :arousal :name "Arousal" :value arousal}]
   :percepts percepts
   :sv-history (add-history sv-history sv)
   :va-history (add-history va-history {:valence valence :arousal arousal})
   :stm stm
   :ltm ltm}
    )

(defn bind-state [{:keys [ws] :as data}]
  (go-loop []
    (when-let [{:keys [message]} (<! ws)]
      (prn message)
      (om/transact! data :data #(make-new-state % (reader/read-string message)))
      (recur))))

(defn bar-view [{:keys [id name value] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (let [layout (:layout opts :vertical)
            layout-class (if (= layout :vertical) "vertical" "horizontal")
            svl (:bar-length opts bar-view-img-length)
            svw (:bar-width opts bar-view-img-width)
            only-positive (:only-positive opts true)
            bar-length (int (+ (* (Math/abs value) svl) 1))
            bar-width svw
            box-length (if only-positive svl (* 2 svl))
            box-width svw
            loffset (cond
                     (and only-positive (= layout :horizontal)) 0
                     (and only-positive
                          (= layout :vertical)) (- (+ svl 1) bar-length)
                     (< value 0.0) (- (+ svl 1) bar-length)
                     :else svl)
            imgsrc (if (< value 0) "red.png" "blue.png")
            width (if (= layout :vertical) bar-width bar-length)
            height (if (= layout :vertical) bar-length bar-width)
            cwidth (if (= layout :vertical) box-width box-length)
            cheight (if (= layout :vertical) box-length box-width)
            ctop (if (= layout :vertical) loffset 0)
            cleft (if (= layout :vertical) 0 loffset)]
        (dom/div #js {:className (str "bar_view" " " layout-class)}
                 (dom/span #js {:className "label"} name)
                 (dom/span #js {:className "value"}
                           (format-float value bar-view-ndp))
                 (dom/div #js {:className "contents"
                               :style #js {:width (str cwidth "px")
                                           :height (str cheight  "px")}}
                          (dom/img #js {:src (str imgroot "/" imgsrc)
                                        :width (str width)
                                        :height (str height)
                                        :style #js {
                                                    :top (str ctop)
                                                    :left (str cleft)}})))))))

(defn valence-arousal-view [{:keys [va] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (apply dom/div #js {:className "valence_arousal"}
             (om/build-all bar-view va
                           {:key :id
                            :opts {:layout :horizontal
                                   :only-positive false}})))))


(defn satisfaction-vector-view [{:keys [sv] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "satisfaction_vector"}
               (dom/h2 nil "Satisfaction vector")
               (apply dom/div #js {:className "contents"}
                      (om/build-all bar-view sv
                                    {:key :id
                                     :opts {:layout :vertical
                                            :only-positive true}})
                        )))))

(defn percept-view
  [{:keys [name] :as c} owner opts]
  (dom/span #js {:className "percept"} name))

(defn percepts-view
  [{:keys [percepts] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "percepts"}
               (apply dom/div #js {:className "contents"}
                      (om/build-all percept-view percepts))))))

(defn motivation-view
  [{:keys [name layer desire decay-rate max-delta] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "motivation"
                    :style #js {:width (:width opts "200px")}}
               (dom/span #js {:className "label"} name)
               (dom/table nil
                          (dom/tr nil
                                  (dom/td nil "Layer")
                                  (dom/td nil (str layer)))
                          (dom/tr nil
                                  (dom/td nil "Desire")
                                  (dom/td nil (format-float desire 4)))
                          (dom/tr nil
                                  (dom/td nil "Decay rate")
                                  (dom/td nil (format-float decay-rate 4)))
                          (dom/tr nil
                                  (dom/td nil "Max delta")
                                  (dom/td nil (format-float max-delta 4)))
                          )))))

(defn motivations-view [{:keys [motivations] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (let [m-width-pc (int (/ 100 (count motivations)))
            m-width-str (str m-width-pc "%")]
        (prn "Motivations" motivations)
        (dom/div #js {:className "motivations"}
                 (dom/h2 nil "Motivations")
                 (apply dom/div #js {:className "contents"}
                        (om/build-all motivation-view motivations
                                      {:key :id
                                       :opts {:width m-width-str}})))))))

(defn history-view [{:keys [sv-history] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div
       (dom/h2 nil "Motivation history")
       (dom/div #js {:id "chart" :width "100%" :height "400px"})))

    om/IDidUpdate
    (did-update [_ _ _]
      (let [n (.getElementById js/document "chart")]
        (while (.hasChildNodes n)
          (.removeChild n (.-lastChild n))))
      (let [id           "chart"
            width        "100%"
            height       600
            bounds       {:x "5%" :y "15%" :width "80%" :height "50%"}
            x-axis       "timestamp"
            y-axis       "value"
            plot         js/dimple.plot.line
            Chart        (.-chart js/dimple)
            svg          (.newSvg js/dimple (str "#" id) width height)
            data         sv-history
            dimple-chart (.setBounds (Chart. svg) (:x bounds) (:y bounds) (:width bounds) (:height bounds))
            x            (.addCategoryAxis dimple-chart "x" x-axis)
            y            (.addMeasureAxis dimple-chart "y" y-axis)
            s            (.addSeries dimple-chart "motivation" plot (clj->js [x y]))]
        (aset s "data" (clj->js data))
        (.addLegend dimple-chart "5%" "10%" "20%" "10%" "right")
        (.draw dimple-chart)
        (.attr (.selectAll (.-shapes x) "text") "transform" "rotate(45,0,12.6015625) translate(5, 0)")
        )
      )
    ))

(defn short-term-item-view
  [{:keys [name other-agents locations stm-entry stm-expiration satisfaction-vector-obs learning-vector] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "memory_item"}
               (dom/span #js {:className "label"} name)
               (dom/table nil
                          (dom/tr nil
                                  (dom/td nil "Entry")
                                  (dom/td nil (format-date stm-entry)))
                          (dom/tr nil
                                  (dom/td nil "Expiration")
                                  (dom/td nil (format-date stm-expiration)))
                          (dom/tr nil
                                  (dom/td nil "Agents")
                                  (dom/td nil (format-agents other-agents)))
                          (dom/tr nil
                                  (dom/td nil "Location")
                                  (dom/td nil (format-locations locations )))
                          (dom/tr nil
                                  (dom/td nil "Observed")
                                  (dom/td nil (format-sv satisfaction-vector-obs)))
                          (dom/tr nil
                                  (dom/td nil "Learning")
                                  (dom/td nil (format-sv learning-vector)))
                          )))))

(defn short-term-memory-view [{:keys [stm] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "memory"}
               (dom/h2 nil "Short-term memory")
               (apply dom/div #js {:className "contents"}
                      (om/build-all short-term-item-view
                                    (sort-by :stm-entry stm)
                                    {:key :id}))))))

(defn long-term-item-view
  [{:keys [name ltm-entry ltm-update-count other-agents locations satisfaction-vector] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "memory_item"}
               (dom/span #js {:className "label"} name)
               (dom/table nil
                          (dom/tr nil
                                  (dom/td nil "Entry")
                                  (dom/td nil (format-date ltm-entry)))
                          (dom/tr nil
                                  (dom/td nil "Updates")
                                  (dom/td nil (str ltm-update-count)))
                          (dom/tr nil
                                  (dom/td nil "Agents")
                                  (dom/td nil (format-agents other-agents)))
                          (dom/tr nil
                                  (dom/td nil "Location")
                                  (dom/td nil (format-locations locations)))
                          (dom/tr nil
                                  (dom/td nil "SV")
                                  (dom/td nil (format-sv satisfaction-vector)))
                          )))))

(defn long-term-memory-view [{:keys [ltm] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "memory"}
               (dom/h2 nil "Long-term memory (percepts)")
               (apply dom/div #js {:className "contents"}
                      (om/build-all long-term-item-view
                                    (sort-by :ltm-entry (:percepts ltm))
                                    {:key :id}))))))

(defn fake-saw-face
  [ch face-id]
  (put! ch (pr-str [:recognised face-id])))

(defn fake-face-recognition-view
  [{:keys [ws] :as c} owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "face_recognition"}
               (dom/h2 nil "Fake face recognition")
               (dom/div #js {:onClick #(fake-saw-face ws :linus_torvalds)}
                        (dom/img #js {:src (str imgroot "/linus_torvalds.jpg")}))
               (dom/div #js {:onClick #(fake-saw-face ws :steve_ballmer)}
                        (dom/img #js {:src (str imgroot "/steve_ballmer.jpg")}))
               (dom/div #js {:onClick #(fake-saw-face ws :nao_robot)}
                        (dom/img #js {:src (str imgroot "/nao_robot.jpg")}))
               ))))

(defn save-robot-address [e owner {:keys [robot-address]}]
  (om/set-state! owner :robot-address (.. e -target -value)))

(defn connect-to-robot [ch owner]
  (let [address (om/get-state owner :robot-address)]
    (prn "Connecting to " address)
    (put! ch (pr-str [:connect address]))))

(defn robot-connect-view
  [{:keys [ws] :as c} owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:robot-address ""})
    om/IRenderState
    (render-state [this state]
      (dom/div #js {:className "robot_connect"}
               (dom/span nil "Robot address")
               (dom/input #js {:type "text"
                               :ref "robot-address"
                               :value (:robot-address state)
                               :onChange #(save-robot-address % owner state)})
               (dom/button #js {:onClick #(connect-to-robot ws owner)}
                           "Connect robot!")))))

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
                        (om/build valence-arousal-view (:data app))
                        (dom/div #js {:className "separator"})
                        (om/build satisfaction-vector-view (:data app)))
               (dom/div #js {:className "separator"})
               (om/build percepts-view (:data app))
               (dom/div #js {:className "separator"})
               (om/build motivations-view (:data app))
               (dom/div #js {:className "separator"})
               (om/build short-term-memory-view (:data app))
               (dom/div #js {:className "separator"})
               (om/build long-term-memory-view (:data app))
               (dom/div #js {:className "separator"})
;               (om/build fake-face-recognition-view app)
;               (dom/div #js {:className "separator"})
               (om/build history-view (:data app))
               (dom/div #js {:className "separator"})
               (om/build robot-connect-view app)
      ))))


(go
  (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:3000/ws"))
        app-state (atom {:ws ws-channel :data {:motivations [] :sv [] :va []
                                       :percepts []
                                       :sv-history []
                                       :va-history []
                                       :stm #{}
                                       :ltm #{}}})]
    (if-not error
      (om/root emotion-display-app app-state
               {:target (.getElementById js/document "app")})
      (js/console.log "Error:" (pr-str error)))))
