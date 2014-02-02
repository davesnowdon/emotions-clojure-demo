(ns emotions-demo.core
  (:require [clojure.core.async :as async
             :refer [<! >! <!! timeout chan alt! put! go go-loop]]
            [clojure.string :refer [trim]]
            [clojure.pprint :refer [pprint]]
            [naojure.core :as nao]
            [emotions.core :refer :all]
            [emotions.util :refer [float=]]))

(def model-update-ms 5000)

; layers from bottom to top
(def demo-layers [:physical :safety :social :skill :contribution])

(def demo-motivations
  [{:id :phys-anger :name "anger" :layer :physical
    :valence -0.7 :arousal 0.7
    :desire 0.0 :decay-rate -0.1 :max-delta 1.0}
   {:id :phys-hunger :name "hunger" :layer :physical
    :valence 0.0 :arousal 0.5
    :desire 0.0 :decay-rate 0.1 :max-delta 1.0}
   {:id :phys-fear :name "fear" :layer :physical
    :valence -0.9 :arousal 0.2
    :desire 0.0 :decay-rate -0.2 :max-delta 1.0}
   {:id :saf-bored :name "bored" :layer :safety
    :valence -0.1 :arousal -0.4
    :desire 0.0 :decay-rate 0.1 :max-delta 0.3}
   {:id :saf-delight :name "delight" :layer :safety
    :valence 0.7 :arousal 0.7
    :desire 0.0 :decay-rate 0.0 :max-delta 0.8}
   {:id :saf-playful :name "playful" :layer :safety
    :valence 0.6 :arousal 0.9
    :desire 0.0 :decay-rate 0.01 :max-delta 0.3}
   {:id :soc-lonely :name "lonely" :layer :social
    :valence -0.6 :arousal -0.6
    :desire 0.0 :decay-rate 0.1 :max-delta 0.3}
   ])

(def demo-control-points
  [{:valence -1.0 :arousal 1.0 :expression-vector
    {:phys-hunger 0.5 :phys-fear 0.8 :saf-bored 0.0 :saf-delight 0.0 :soc-lonely 0.2}}

   {:valence 0.0 :arousal 1.0 :expression-vector
    {:phys-hunger 0.0 :phys-fear 0.0  :saf-bored 0.5 :saf-delight 0.8 :soc-lonely 0.0}}

   {:valence 1.0 :arousal 1.0 :expression-vector
    {:phys-hunger 0.0 :phys-fear 0.0 :saf-bored 0.0 :saf-delight 0.9 :soc-lonely 0.0}}

   {:valence -1.0 :arousal 0.0 :expression-vector
    {:phys-hunger 0.1 :phys-fear 0.5 :saf-bored 0.3 :saf-delight 0.0 :soc-lonely 0.1}}

   {:valence 0.0 :arousal 0.0 :expression-vector
    {:phys-hunger 0.5 :phys-fear 0.1 :saf-bored 0.8 :saf-delight 0.0 :soc-lonely 0.3}}

   {:valence 1.0 :arousal 0.0 :expression-vector
    {:phys-hunger 0.0 :phys-fear 0.0 :saf-bored 0.0 :saf-delight 0.5 :soc-lonely 0.0}}

   {:valence -1.0 :arousal -1.0 :expression-vector
    {:phys-hunger 0.0 :phys-fear 0.0 :saf-bored 0.3 :saf-delight 0.0 :soc-lonely 0.0}}

   {:valence 0.0 :arousal -1.0 :expression-vector
    {:phys-hunger 0.0 :phys-fear 0.0 :saf-bored 0.0 :saf-delight 0.0 :soc-lonely 0.0}}

   {:valence 1.0 :arousal -1.0 :expression-vector
    {:phys-hunger 0.0 :phys-fear 0.0 :saf-bored 0.0 :saf-delight 0.0 :soc-lonely 0.0}}
   ])

;; TODO percepts
;; - see Ballmer
;; - see person
;; - see friend
;; - battery level
;; - do angry behaviour
;; - do hello
;; - do scared
;; - do happy
;; - need a bored behaviour

(defn emotions-process
  "Loop that receives percepts and updates the emotional model"
  [percept-chan state-chan]
  (let [initial-sv (motivations->sv demo-motivations)
        layers demo-layers
        control-points demo-control-points]
    (go-loop [sv initial-sv
              motivations demo-motivations
              percepts []
              timer (timeout (long model-update-ms))]
             (let [[v c] (alts! [timer percept-chan] :priority true)]
               (condp = c

                 percept-chan
                 (when v
                   (recur sv motivations
                          (conj percepts v)
                          timer))

                 timer
                 (let [[new-motivations new-sv]
                       (percepts->motivations+sv layers
                                                 motivations
                                                 percepts)
                       {valence :valence arousal :arousal}
                       (sv->valence+arousal control-points new-sv)]
                   (>! state-chan {:sv new-sv
                                   :valence valence
                                   :arousal arousal
                                   :percepts percepts})
                   (recur new-sv new-motivations []
                          (timeout (long model-update-ms)))))))))

(defn state-emitter
  [state-chan clients-atom]
  (go-loop [state (<! state-chan)]
           (when state
             (doseq [client-chan @clients-atom]
               (>! client-chan state))
             (recur (<! state-chan)))))

(defn state-display
  [display-chan]
  (go-loop [state (<! display-chan)]
           (let [{:keys [sv valence arousal percepts]} state]
             (println "Valence:" valence "Arousal:" arousal)
             (doseq [[k v] sv]
               (println "SV:  " k " - " v))
             (if (seq percepts)
               (doseq [p percepts]
                 (println "Percept:" (:name p))))
             (println "--\n")
             (recur (<! display-chan)))))

(defn head-touch-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
           (do
             (if (float= 1.0 value)
               (>! percept-chan
                   {:name "Head touched" :satisfaction-vector
                    {:phys-anger 0.2
                     :saf-bored -0.3
                     :saf-delight 0.1}}))
             (recur (<! event-chan)))))

(defn hand-touch-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
           (do
             (if (float= 1.0 value)
               (>! percept-chan
                   {:name "Hand touched" :satisfaction-vector
                    {:phys-anger -0.2
                     :saf-bored -0.1
                     :saf-delight -0.1
                     :soc-lonely -0.3}}))
             (recur (<! event-chan)))))

(defn foot-touch-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
           (do
             (if (float= 1.0 value)
               (>! percept-chan
                   {:name "Foot touched" :satisfaction-vector
                    {:phys-anger -0.1
                     :saf-bored -0.1
                     :saf-playful 0.2}}))
             (recur (<! event-chan)))))

(defn run-demo
  [hostname]
  (do
    (println "Connecting to" hostname)
    (let [robot (nao/make-robot hostname 9559 [:motion :tts])
          percept-chan (chan)
          state-chan (chan)
          display-chan (chan)
          head-chan (chan)
          hand-chan (chan)
          foot-chan (chan)
          clients-atom (atom [display-chan])]
      (nao/say robot "I'm starting to feel quite emotional")
      (let [ev-robot
            (-> robot
                (nao/add-event-chan "FrontTactilTouched" head-chan)
                (nao/add-event-chan "MiddleTactilTouched" head-chan)
                (nao/add-event-chan "RearTactilTouched" head-chan)
                (nao/add-event-chan "HandLeftBackTouched" hand-chan)
                (nao/add-event-chan "HandRightBackTouched" hand-chan)
                (nao/add-event-chan "LeftBumperPressed" foot-chan)
                (nao/add-event-chan "RightBumperPressed" foot-chan))]
        (state-emitter state-chan clients-atom)
        (state-display display-chan)
        (head-touch-process head-chan percept-chan)
        (hand-touch-process hand-chan percept-chan)
        (foot-touch-process foot-chan percept-chan)
        (<!! (emotions-process percept-chan state-chan))
        ))))

(defn -main
  [& args]
  (run-demo (first args)))
