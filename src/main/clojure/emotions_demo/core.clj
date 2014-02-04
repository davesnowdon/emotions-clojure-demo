(ns emotions-demo.core
  (:require [clojure.core.async :as async
             :refer [<! >! <!! timeout chan alt! put! go go-loop]]
            [clojure.string :refer [trim]]
            [clojure.pprint :refer [pprint]]
            [naojure.core :as nao]
            [emotions.core :refer :all]
            [emotions.util :refer [float=]]))

(def model-update-ms 2000)

; layers from bottom to top
(def demo-layers [:physical :safety :social :skill :contribution])

(def demo-layer-multipliers
  {:physical 0.75 :safety 0.5 :social 0.4 :skill 0.25 :contribution 0.25})

(def demo-motivations
  [{:id :phys-anger :name "anger" :layer :physical
    :valence -0.7 :arousal 0.7
    :desire 0.0 :decay-rate -0.02 :max-delta 1.0}
   {:id :phys-hunger :name "hunger" :layer :physical
    :valence 0.0 :arousal 0.5
    :desire 0.0 :decay-rate 0.0 :max-delta 1.0}
   {:id :phys-fear :name "fear" :layer :physical
    :valence -0.9 :arousal 0.2
    :desire 0.0 :decay-rate -0.2 :max-delta 1.0}
   {:id :saf-bored :name "bored" :layer :safety
    :valence -0.1 :arousal -0.4
    :desire 0.0 :decay-rate 0.02 :max-delta 0.3}
   {:id :saf-delight :name "delight" :layer :safety
    :valence 0.7 :arousal 0.7
    :desire 0.0 :decay-rate 0.0 :max-delta 0.8}
   {:id :saf-playful :name "playful" :layer :safety
    :valence 0.6 :arousal 0.9
    :desire 0.0 :decay-rate 0.01 :max-delta 0.3}
   {:id :soc-lonely :name "lonely" :layer :social
    :valence -0.6 :arousal -0.6
    :desire 0.0 :decay-rate 0.01 :max-delta 0.3}
   ])

(def demo-control-points
  [{:valence -1.0 :arousal 1.0 :expression-vector
    {:phys-anger 0.7 :phys-fear 0.8}}

   {:valence 0.0 :arousal 1.0 :expression-vector
    {:phys-hunger 0.5 :saf-playful 0.9}}

   {:valence 1.0 :arousal 1.0 :expression-vector
    {:saf-delight 0.7 :saf-playful 0.7}}

   {:valence -1.0 :arousal 0.0 :expression-vector
    {:phys-fear 0.9}}

   {:valence 0.0 :arousal 0.0 :expression-vector
    {:saf-bored 0.5}}

   {:valence 1.0 :arousal 0.0 :expression-vector
    {:saf-delight 0.9}}

   {:valence -1.0 :arousal -1.0 :expression-vector
    {:soc-lonely 0.6}}

   {:valence 0.0 :arousal -1.0 :expression-vector
    {:saf-bored 0.5}}

   {:valence 1.0 :arousal -1.0 :expression-vector
    {}}
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
        layer-multiplers demo-layer-multipliers
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
                                                 layer-multiplers
                                                 motivations
                                                 percepts)
                       {valence :valence arousal :arousal}
                       (sv->valence+arousal control-points new-sv)]
;;                   (pprint new-motivations)
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

(defn reaction-process
  "Triggers a reaction and the resulting percept when a predicate is met"
  [robot percept-chan predicate reaction percept]
  (let [state-chan (chan)
        reaction-complete-chan (chan)]
    (go (while true
          (let [state (<! state-chan)]
            (if (predicate state)
              (reaction robot state reaction-complete-chan)))))
    (go (while true
          (let [done (<! reaction-complete-chan)]
            (>! percept-chan (percept done)))))
    state-chan))

(defn react-angrily?
  [state]
  (> (get-in state [:sv :phys-anger] 0.0) 0.75))

(defn anger-reaction
  [robot state complete-chan]
  (if (> (rand) 0.5)
    (nao/run-behaviour robot "dsnowdon-angry" complete-chan)
    (nao/run-behaviour robot "dsnowdon-exterminate" complete-chan)))

(defn anger-reaction-percept
  [done-signal]
  {:name "Got angry" :satisfaction-vector
   {:phys-anger -0.5
    :saf-bored -0.3}})

(defn anger-management
  [robot percept-chan]
  (reaction-process robot percept-chan
                    react-angrily? anger-reaction anger-reaction-percept))

(defn react-lonely?
  [state]
  (> (get-in state [:sv :soc-lonely] 0.0) 0.5))

(defn lonely-reaction
  [robot state complete-chan]
  (->
   (nao/say robot "Is anyone there? I'm feeling lonely.")
   (nao/future-callback-wrapper
    (nao/callback->channel complete-chan))))

(defn lonely-reaction-percept
  [done-signal]
  {:name "Very lonely" :satisfaction-vector
   {:soc-lonely -0.4
    :saf-bored 0.1}})

(defn loneliness-management
  [robot percept-chan]
  (reaction-process robot percept-chan
                    react-lonely? lonely-reaction lonely-reaction-percept))

(defn react-bored?
  [state]
  (> (get-in state [:sv :saf-bored] 0.0) 0.75))

(defn bored-reaction
  [robot state complete-chan]
  (if (> (rand) 0.5)
    (->
     (nao/say robot "Ho hum! I'm bored.")
     (nao/future-callback-wrapper
      (nao/callback->channel complete-chan)))
    (nao/run-behaviour robot "dsnowdon-hello" complete-chan)))

(defn bored-reaction-percept
  [done-signal]
  {:name "Bored" :satisfaction-vector
   {:soc-lonely 0.05
    :saf-bored -0.1}})

(defn react-scared?
  [state]
  (> (get-in state [:sv :phys-fear] 0.0) 0.6))

(defn fear-reaction
  [robot state complete-chan]
  (nao/run-behaviour robot "dsnowdon-scared" complete-chan))

(defn fear-reaction-percept
  [done-signal]
  {:name "Got scared" :satisfaction-vector
   {:phys-fear -0.2
    :saf-bored -0.5}})

(defn fear-management
  [robot percept-chan]
  (reaction-process robot percept-chan
                    react-scared? fear-reaction fear-reaction-percept))

(defn react-happy?
  [state]
  (or
   (> (get-in state [:sv :saf-delight] 0.0) 0.5)
   (> (get-in state [:sv :saf-playful] 0.0) 0.5)))

(defn delight-reaction
  [robot state complete-chan]
  (nao/run-behaviour robot "dsnowdon-happy" complete-chan))

(defn delight-reaction-percept
  [done-signal]
  {:name "Happy & Playful" :satisfaction-vector
   {:saf-delight -0.4
    :saf-playful -0.4}})

(defn delight-management
  [robot percept-chan]
  (reaction-process robot percept-chan
                    react-happy? delight-reaction delight-reaction-percept))

(defn boredom-management
  [robot percept-chan]
  (reaction-process robot percept-chan
                    react-bored? bored-reaction bored-reaction-percept))

(defn head-touch-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
           (do
             (if (float= 1.0 value)
               (>! percept-chan
                   {:name "Head touched" :satisfaction-vector
                    {:phys-anger 0.2
                     :saf-bored -0.3}}))
             (recur (<! event-chan)))))

(defn back-head-touch-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
           (do
             (if (float= 1.0 value)
               (>! percept-chan
                   {:name "Back Head touched" :satisfaction-vector
                    {:phys-fear 1.0
                     :saf-bored -0.3}}))
             (recur (<! event-chan)))))

(defn hand-touch-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
           (do
             (if (float= 1.0 value)
               (>! percept-chan
                   {:name "Hand touched" :satisfaction-vector
                    {:phys-anger -0.2
                     :saf-bored -0.2
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
                     :saf-bored -0.2
                     :saf-playful 0.2}}))
             (recur (<! event-chan)))))

(defn face-detected-process
  [event-chan percept-chan]
  (go (while true
        (let [[event value] (<! event-chan)]
          (println "Face" value)))))

(defn add-state-listener
  [clients-atom listener-chan]
  (swap! clients-atom conj listener-chan))

(defn run-demo
  [hostname]
  (do
    (println "Connecting to" hostname)
    (let [robot (nao/make-robot hostname 9559 [:motion :tts])
          percept-chan (chan)
          state-chan (chan)
          display-chan (chan)
          head-chan (chan)
          back-head-chan (chan)
          hand-chan (chan)
          foot-chan (chan)
          face-chan (chan)
          clients-atom (atom [display-chan])]
      (nao/set-volume robot (Float. 1.0))
      (nao/say robot "I'm starting to feel quite emotional")
      (let [ev-robot
            (-> robot
                (nao/add-event-chan "FrontTactilTouched" head-chan)
                (nao/add-event-chan "MiddleTactilTouched" head-chan)
                (nao/add-event-chan "RearTactilTouched" back-head-chan)
                (nao/add-event-chan "HandLeftBackTouched" hand-chan)
                (nao/add-event-chan "HandRightBackTouched" hand-chan)
                (nao/add-event-chan "LeftBumperPressed" foot-chan)
                (nao/add-event-chan "RightBumperPressed" foot-chan)
                (nao/add-event-chan "FaceDetected" face-chan))]
        (state-emitter state-chan clients-atom)
        (state-display display-chan)
        (head-touch-process head-chan percept-chan)
        (back-head-touch-process back-head-chan percept-chan)
        (hand-touch-process hand-chan percept-chan)
        (foot-touch-process foot-chan percept-chan)
        (face-detected-process face-chan percept-chan)
        (add-state-listener clients-atom
                            (anger-management ev-robot percept-chan))
        (add-state-listener clients-atom
                            (loneliness-management ev-robot percept-chan))
        (add-state-listener clients-atom
                            (boredom-management ev-robot percept-chan))
        (add-state-listener clients-atom
                            (fear-management ev-robot percept-chan))
        (add-state-listener clients-atom
                            (delight-management ev-robot percept-chan))
        (<!! (emotions-process percept-chan state-chan))
        ))))

(defn -main
  [& args]
  (run-demo (first args)))
