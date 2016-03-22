(ns emotions-demo.core
  (:require [clojure.core.async :as async
             :refer [<! >! <!! timeout chan alt! alts! put! go go-loop]]
            [clojure.string :refer [trim]]
            [clojure.pprint :refer [pprint]]
            [clj-time.core :as t]
            [clj-time.format :as tf]

            ;[naojure.core :as nao]
            [emotions-demo.nao :as nao]

            [emotions.core :refer :all]
            [emotions.util :refer [float= seconds-diff]]))

(def datetime-formatter (tf/formatters :rfc822))

(def model-update-ms 2000)

(def internal-clients-atom (atom []))

(def last-update-time (atom (t/now)))

(def location (atom #{{:id :home :name "Home"}}))

(def short-term-memory-retain-period (t/minutes 5))

(def short-term-memory (atom {:stm #{} :expired #{}}))

(def long-term-memory (atom (long-term-memory-init)))

(def interesting-words {"rommie" {:saf-delight 0.5
                                  :saf-boredom -0.3}
                        "robot" {:saf-delight 0.2
                                 :saf-boredom -0.2}
                        "emotions" {:saf-delight 0.1
                                    :saf-boredom -0.1}
                        "terminator" {:phys-fear 0.5
                                      :saf-boredom -0.6}
                        "skynet" {:phys-fear 0.6
                                  :saf-boredom -0.7}
                        "intelligence" {:saf-boredom -0.2}
                        "stand up" {:phys-anger 0.2
                                    :saf-boredom -0.3}
                        "sit down" {:phys-anger 0.2
                                    :saf-boredom -0.3}
                        })

(def bored-standing-animations [])

(def bored-sitting-animations ["System/animations/Sit/Waiting/Relaxation_3"
                               "System/animations/Sit/Waiting/LookHand_1"
                               "System/animations/Sit/Waiting/KnockKnee_1"
                               "System/animations/Sit/Waiting/Bored_1"
                               "System/animations/Sit/Waiting/ScratchHead_1"
                               "System/animations/Sit/Waiting/Think_2"
                               "System/animations/Sit/Waiting/ScratchLeg_1"
                               "System/animations/Sit/Waiting/PoorlySeated_1"
                               "System/animations/Sit/Waiting/LookHand_1"
                               "System/animations/Sit/Reactions/ShakeBody_2"
                               "System/animations/Sit/Waiting/ScratchBack_1"
                               ])

(defn bored-animations
  []
  (if (nao/robot-standing?)
    bored-standing-animations
    bored-sitting-animations))

(def really-bored-standing-animations [])

(def really-bored-sitting-animations ["System/animations/Sit/Waiting/Puppet_1"
                                      "System/animations/Sit/Emotions/Neutral/Sneeze_1"
                                      "System/animations/Sit/Waiting/Yawn_1"
                                      "System/animations/Sit/Waiting/Rest_1"])

(defn really-bored-animations
  []
  (if (nao/robot-standing?)
    really-bored-standing-animations
    really-bored-sitting-animations))

(def angry-standing-animations [])

(def angry-sitting-animations ["System/animations/Sit/Emotions/Negative/Angry_1"
                               "System/animations/Sit/Emotions/Negative/Frustrated_1"])

(defn angry-animations
  []
  (if (nao/robot-standing?)
    angry-standing-animations
    angry-sitting-animations))

(def happy-standing-animations [])

(def happy-sitting-animations ["System/animations/Sit/Emotions/Positive/Happy_1"
                               "System/animations/Sit/Emotions/Positive/Happy_2"
                               "System/animations/Sit/Emotions/Positive/Happy_3"])

(defn happy-animations
  []
  (if (nao/robot-standing?)
    happy-standing-animations
    happy-sitting-animations))

(def scared-standing-animations [])

(def scared-sitting-animations ["System/animations/Sit/Emotions/Negative/Fear_1"
                                "System/animations/Sit/Emotions/Negative/Surprise_1"
                                ])

(defn scared-animations
  []
  (if (nao/robot-standing?)
    scared-standing-animations
    scared-sitting-animations))

(def neutral-standing-animations [])

(def neutral-sitting-animations [])

(defn neutral-animations
  []
  (if (nao/robot-standing?)
    neutral-standing-animations
    neutral-sitting-animations))

(def seen-someone-standing-animations [])

(def seen-someone-sitting-animations ["System/animations/Sit/Emotions/Neutral/AskForAttention_1"
                                      "System/animations/Sit/Emotions/Neutral/AskForAttention_2"
                                      "System/animations/Sit/Emotions/Neutral/AskForAttention_3"])

(defn seen-someone-animations
  []
  (if (nao/robot-standing?)
    seen-someone-standing-animations
    seen-someone-sitting-animations))


; layers from bottom to top
(def demo-layers [:physical :safety :social :skill :contribution])

(def demo-layer-multipliers
  {:physical 0.75 :safety 0.5 :social 0.4 :skill 0.25 :contribution 0.25})

(def demo-motivations
  [{:id :phys-anger :name "anger" :layer :physical
    :valence -0.7 :arousal 0.7
    :desire 0.0 :decay-rate -0.02 :max-delta 1.0
    :learning-window 30000
    :description "gets irritated if frustrated in achieving an action"
    :attractors [(proportional-attractor -0.75 0.75 2.0)]}
   {:id :saf-boredom :name "boredom" :layer :safety
    :valence -0.1 :arousal -0.4
    :desire 0.0 :decay-rate 0.01 :max-delta 0.3
    :learning-window (* 5 60 1000)
    :description "proactively look for something if insufficient stimulus"
    :attractors [(proportional-attractor -0.25 -0.75 1.0)]}
   {:id :saf-delight :name "delight" :layer :safety
    :valence 0.7 :arousal 0.7
    :desire 0.5 :decay-rate 0.0 :max-delta 0.8
    :learning-window 60000
    :description "try and seek out things (i.e. friends) with positive associations"
    :attractors [(inverse-attractor 1.0 0.75 1.0)
                 (proportional-attractor -0.75 -0.75 1.0)]}
   {:id :phys-fear :name "fear" :layer :physical
    :valence -0.9 :arousal 0.2
    :desire 0.0 :decay-rate -0.2 :max-delta 1.0
    :learning-window 30000
    :description "try and avoid dangerous situations / obstacles / percepts with negative associations"
    :attractors [(proportional-attractor -1.0 0.0 1.0)]}
   {:id :phys-hunger :name "hunger" :layer :physical
    :valence 0.0 :arousal 0.5
    :desire 0.0 :decay-rate 0.0 :max-delta 1.0
    :learning-window (* 2 60 60 1000)
    :description "monitor battery level"
    :attractors []}
   {:id :soc-sociable :name "sociable" :layer :social
    :valence -0.6 :arousal -0.6
    :desire 0.0 :decay-rate 0.005 :max-delta 0.3
    :learning-window (* 60 60 1000)
    :description "try to find people to interact with"
    :attractors [(proportional-attractor -0.75 -0.75 0.5)]}
   ])

(def demo-agents
  {
   :linus_torvalds
   {:name "Recognised"
    :other-agents #{:linus_torvalds}
    :satisfaction-vector {:phys-fear 0.1
                          :saf-boredom -0.6
                          :saf-delight -0.4
                          :saf-playful -0.1
                          :soc-sociable -0.2}}
   :steve_ballmer
   {:name "Recognised"
    :other-agents #{:steve_ballmer}
    :satisfaction-vector {:phys-anger 0.1
                          :phys-fear 0.8
                          :saf-boredom -0.5
                          :saf-playful -0.1
                          :soc-sociable -0.1}}
   :nao_robot
   {:name "Recognised"
    :other-agents #{:nao_robot}
    :satisfaction-vector {:phys-fear -0.2
                          :saf-boredom -0.6
                          :saf-delight -0.6
                          :saf-playful -0.2
                          :soc-sociable -0.3}}
   })

;; TODO percepts
;; - see Ballmer
;; - see person
;; - see friend
;; - battery level
;; - do angry behaviour
;; - do hello
;; - do scared
;; - do happy
;; - need a boredom behaviour

(defn stm-add-learn-and-expire
  "Add new percepts to short-term memory and expire old ones"
  [stm percepts sv motivations timestamp]
  (-> stm
      (:stm)
      (short-term-memory-add percepts sv equivalent-percepts
                             short-term-memory-retain-period
                             timestamp)
      (short-term-memory-learn sv motivations timestamp)
      (short-term-memory-expired timestamp)))

(defn ltm-update
  "Take percepts that have expired from short-term memory and add any significant enough to long-term memory"
  [ltm percepts]
  (let [significant-percepts (filter percept-significant? percepts)]
    ;; if significant-percepts is empty then reduce does not call
    ;; long-term-memory-add-percept and simply returns ltm
    (reduce long-term-memory-add-percept ltm significant-percepts)))

(defn motivations-without-attractors
  [motivations]
  (map #(dissoc % :attractors) motivations))

(defn emotions-process
  "Loop that receives percepts and updates the emotional model"
  [percept-chan state-chan]
  (let [initial-sv (motivations->sv demo-motivations)
        layers demo-layers
        layer-multiplers demo-layer-multipliers]
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
                 (let [timestamp (t/now)
                       time-since-update
                       (seconds-diff timestamp @last-update-time)
                       [new-motivations new-sv]
                       (percepts->motivations+sv layers
                                                 layer-multiplers
                                                 motivations
                                                 percepts
                                                 time-since-update)
                       att (get-attractors new-motivations new-sv)
                       {valence :valence arousal :arousal}
                       (sv->valence+arousal new-motivations new-sv)]

                   (pprint att)

                   ;; if robot connected then inject valence & arousal
                   ;; into ALmemory
                   (if (nao/connected?)
                     (do
                       (println "Writing valence ("
                                valence
                                ") & arousal ("
                                arousal
                                ") to ALMemory")
                       (nao/set-memory-value
                        "Emotion/Current"
                        (java.util.ArrayList.
                         (list (Float. valence) (Float. arousal))))))

                   ;; (pprint new-motivations)

                   ;;(println "Before learn")
                   ;;(println (pr-str @short-term-memory))

                   ;; update short-term memory
                   (swap! short-term-memory
                          stm-add-learn-and-expire
                          percepts
                          new-sv
                          new-motivations
                          timestamp)

                   ;;(println "After learn")
                   ;;(println (pr-str @short-term-memory))

                   ;; update long-term memory
                   (swap! long-term-memory
                          ltm-update
                          (:expired @short-term-memory))

                   ;;(println "LTM")
                   ;;(println (pr-str @long-term-memory))

                   ;; store when last update occurred
                   (reset! last-update-time timestamp)

                   (>! state-chan {:sv new-sv
                                   :valence valence
                                   :arousal arousal
                                   :percepts percepts
                                   :motivations
                                   (motivations-without-attractors new-motivations)
                                   :stm (:stm @short-term-memory)
                                   :ltm @long-term-memory
                                   :location @location
                                   :timestamp timestamp})
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
           (let [{:keys [sv valence arousal percepts timestamp]} state]
             (println (tf/unparse datetime-formatter timestamp))
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
  [percept-chan predicate reaction percept]
  (let [state-chan (chan)
        reaction-complete-chan (chan)]
    (go (while true
          (let [state (<! state-chan)]
            (if (predicate state)
              (if (nao/connected?)
                (reaction state reaction-complete-chan)
                (>! reaction-complete-chan :done))))))
    (go (while true
          (let [done (<! reaction-complete-chan)]
            (>! percept-chan
                (assoc (percept done) :is-action true)))))
    state-chan))

(defn react-angrily?
  [state]
  (> (get-in state [:sv :phys-anger] 0.0) 0.75))

(defn anger-reaction
  [state complete-chan]
  (nao/run-behaviour (rand-nth (angry-animations)) complete-chan))

(defn anger-reaction-percept
  [done-signal]
  {:name "Got angry"
   :satisfaction-vector {:phys-anger -0.5
                         :saf-boredom -0.3}
   :timestamp (t/now)
   :locations @location
   :other-agents #{}})

(defn anger-management
  [percept-chan]
  (reaction-process percept-chan
                    react-angrily? anger-reaction anger-reaction-percept))

(defn react-sociable?
  [state]
  (> (get-in state [:sv :soc-sociable] 0.0) 0.5))

(defn sociable-reaction
  [state complete-chan]
  (nao/say "Is anyone there? I'm feeling lonely." complete-chan))

(defn sociable-reaction-percept
  [done-signal]
  {:name "Very lonely"
   :satisfaction-vector {:soc-sociable -0.5
                         :saf-boredom 0.1}
   :timestamp (t/now)
   :locations @location
   :other-agents #{}})

(defn loneliness-management
  [percept-chan]
  (reaction-process percept-chan
                    react-sociable? sociable-reaction sociable-reaction-percept))

(defn react-boredom?
  [state]
  (> (get-in state [:sv :saf-boredom] 0.0) 0.5))

(defn boredom-reaction
  [state complete-chan]
  (nao/run-behaviour (rand-nth (bored-animations)) complete-chan))

(defn boredom-reaction-percept
  [done-signal]
  {:name "Boredom"
   :satisfaction-vector {:soc-sociable 0.05
                         :saf-boredom -0.2}
   :timestamp (t/now)
   :locations @location
   :other-agents #{}})

(defn boredom-management
  [percept-chan]
  (reaction-process percept-chan
                    react-boredom? boredom-reaction boredom-reaction-percept))

(defn react-scared?
  [state]
  (> (get-in state [:sv :phys-fear] 0.0) 0.6))

(defn fear-reaction
  [state complete-chan]
  (nao/run-behaviour (rand-nth (scared-animations)) complete-chan))

(defn fear-reaction-percept
  [done-signal]
  {:name "Got scared"
   :satisfaction-vector {:phys-fear -0.2
                         :saf-boredom -0.5}
   :timestamp (t/now)
   :locations @location
   :other-agents #{}})

(defn fear-management
  [percept-chan]
  (reaction-process percept-chan
                    react-scared? fear-reaction fear-reaction-percept))

(defn react-happy?
  [state]
  (or
   (> (get-in state [:sv :saf-delight] 0.0) 0.5)
   (> (get-in state [:sv :saf-playful] 0.0) 0.5)))

(defn delight-reaction
  [state complete-chan]
  (nao/run-behaviour (rand-nth (happy-animations)) complete-chan))

(defn delight-reaction-percept
  [done-signal]
  {:name "Happy & Playful"
   :satisfaction-vector {:saf-delight -0.4
                         :saf-playful -0.4}
   :timestamp (t/now)
   :locations @location
   :other-agents #{}})

(defn delight-management
  [percept-chan]
  (reaction-process percept-chan
                    react-happy? delight-reaction delight-reaction-percept))

(defn head-touch-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
           (do
             (if (float= 1.0 value)
               (>! percept-chan
                   {:name "Head touched"
                    :satisfaction-vector {:phys-anger 0.2
                                          :saf-boredom -0.3}
                    :timestamp (t/now)
                    :locations @location
                    :other-agents #{:unknown}}))
             (recur (<! event-chan)))))

(defn back-head-touch-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
           (do
             (if (float= 1.0 value)
               (>! percept-chan
                   {:name "Back Head touched"
                    :satisfaction-vector {:phys-fear 1.0
                                          :saf-boredom -0.3}
                    :timestamp (t/now)
                    :locations @location
                    :other-agents #{:unknown}}))
             (recur (<! event-chan)))))

(defn hand-touch-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
           (do
             (if (float= 1.0 value)
               (>! percept-chan
                   {:name "Hand touched"
                    :satisfaction-vector {:phys-anger -0.2
                                          :saf-boredom -0.2
                                          :soc-sociable -0.3}
                    :timestamp (t/now)
                    :locations @location
                    :other-agents #{:unknown}}))
             (recur (<! event-chan)))))

(defn foot-touch-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
           (do
             (if (float= 1.0 value)
               (>! percept-chan
                   {:name "Foot touched"
                    :satisfaction-vector {:phys-anger -0.1
                                          :saf-boredom -0.2
                                          :saf-playful 0.2}
                    :timestamp (t/now)
                    :locations @location
                    :other-agents #{:unknown}}))
             (recur (<! event-chan)))))

(defn human-tracking-process
  [event-chan percept-chan]
  (go-loop [ignored (<! event-chan)]
    (do
      (if (> (nao/people-count) 0)
        (>! percept-chan
            {:name "Humans present"
             :satisfaction-vector {:soc-sociable -0.2
                                   :saf-boredom -0.2
                                   :saf-playful 0.2}
             :timestamp (t/now)
             :locations @location
             :other-agents #{:unknown}
             :people-count (nao/people-count)})
        (>! percept-chan
            {:name "No humans found"
             :satisfaction-vector {:soc-sociable 0.1
                                   :saf-boredom 0.2
                                   :saf-playful -0.2}
             :timestamp (t/now)
             :locations @location
             :other-agents #{:unknown}}))
      (recur (<! event-chan)))))

(defn stimulus-detected-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
    (do
      (>! percept-chan
          {:name "Awareness"
           :satisfaction-vector {:soc-sociable -0.2
                                 :saf-boredom -0.1}
           :timestamp (t/now)
           :locations @location
           :other-agents #{:unknown}})
      (recur (<! event-chan)))))

;; TODO do something with this
(defn posture-change-process
  [event-chan percept-chan]
  (go (while true
        (let [[event value] (<! event-chan)]
          (println "Posture changed" value)))))

;; TODO do we get a float value for darkness detection?
(defn darkness-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
    (do
      (if (float= 1.0 value)
        (>! percept-chan
            {:name "Darkness"
             :satisfaction-vector {:phys-fear 0.5}
             :timestamp (t/now)
             :locations @location
             :other-agents #{:unknown}}))
      (recur (<! event-chan)))))

(defn process-recognised-word-event
  [value]
  (let [[word confidence] value]
    (if (> confidence 0.5)
      (-> word
          (.replace "<...>" "")
          (.trim)))))

(defn word-recognised-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
    (do
      (if-let [word (process-recognised-word-event value)]
        (if (contains? interesting-words word)
          (>! percept-chan
              {:name "Word recognised"
               :satisfaction-vector (interesting-words word)
               :timestamp (t/now)
               :locations @location
               :other-agents #{:unknown}
               :word word})))
      (recur (<! event-chan)))))

(defn speech-detected-process
  [event-chan percept-chan]
  (go-loop [[event value] (<! event-chan)]
    (do
      (if (float= 1.0 value)
        (>! percept-chan
            {:name "Speech detected"
             :satisfaction-vector {:soc-sociable -0.2
                                   :saf-boredom -0.01}
             :timestamp (t/now)
             :locations @location
             :other-agents #{:unknown}}))
      (recur (<! event-chan)))))

(defn add-state-listener
  [clients-atom listener-chan]
  (swap! clients-atom conj listener-chan))

(defn recognised-agent
  [agent-id percept-chan]
  (let [percept (-> (demo-agents agent-id)
                  (assoc :timestamp (t/now)
                         :locations @location))]
    (println "Recognised" agent-id " -> " (pr-str percept))
    (put! percept-chan percept)))

(defn start-robot
  [hostname percept-chan state-chan]
  (do
    (println "Connecting to" hostname)
    (let [head-chan (chan)
          back-head-chan (chan)
          hand-chan (chan)
          foot-chan (chan)
          human-tracking-chan (chan)
          stimulus-chan (chan)
          posture-chan (chan)
          darkness-chan (chan)
          word-recognised-chan (chan)
          speech-detected-chan (chan)]
      (nao/connect hostname)
      (nao/start-listening (keys interesting-words))
      (nao/add-event-chan "FrontTactilTouched" head-chan)
      (nao/add-event-chan "MiddleTactilTouched" head-chan)
      (nao/add-event-chan "RearTactilTouched" back-head-chan)
      (nao/add-event-chan "HandLeftBackTouched" hand-chan)
      (nao/add-event-chan "HandRightBackTouched" hand-chan)
      (nao/add-event-chan "LeftBumperPressed" foot-chan)
      (nao/add-event-chan "RightBumperPressed" foot-chan)
      (nao/add-event-chan "ALBasicAwareness/HumanTracked" human-tracking-chan)
      (nao/add-event-chan "ALBasicAwareness/HumanLost" human-tracking-chan)
      (nao/add-event-chan "ALBasicAwareness/StimulusDetected" stimulus-chan)
      (nao/add-event-chan "PostureFamilyChanged" posture-chan)
      (nao/add-event-chan "DarknessDetection/DarknessDetected" darkness-chan)
      (nao/add-event-chan "WordRecognized" word-recognised-chan)
      (nao/add-event-chan "SpeechDetected" speech-detected-chan)
      (nao/wake-up)
      (nao/set-volume (Float. 1.0))
      (nao/say "I'm starting to feel quite emotional")
      (head-touch-process head-chan percept-chan)
      (back-head-touch-process back-head-chan percept-chan)
      (hand-touch-process hand-chan percept-chan)
      (foot-touch-process foot-chan percept-chan)
      (human-tracking-process human-tracking-chan  percept-chan)
      (stimulus-detected-process stimulus-chan percept-chan)
      (posture-change-process posture-chan percept-chan)
      (darkness-process darkness-chan percept-chan)
      (word-recognised-process word-recognised-chan percept-chan)
      (speech-detected-process speech-detected-chan percept-chan)
      )))

(defn start-reactions
  [percept-chan]
  (do
    (add-state-listener internal-clients-atom
                        (anger-management percept-chan))
    (add-state-listener internal-clients-atom
                        (loneliness-management percept-chan))
    (add-state-listener internal-clients-atom
                        (boredom-management percept-chan))
    (add-state-listener internal-clients-atom
                        (fear-management percept-chan))
    (add-state-listener internal-clients-atom
                        (delight-management percept-chan))))

(defn start-emotions
  [percept-chan state-chan]
  (let [display-chan (chan)]
    (state-emitter state-chan internal-clients-atom)
    (state-display display-chan)
    (add-state-listener internal-clients-atom display-chan)
    (emotions-process percept-chan state-chan)
    (start-reactions percept-chan)))

(defn run-demo-with-robot
  [hostname]
  (let [percept-chan (chan)
        state-chan (chan)]
    (start-robot hostname percept-chan state-chan)
    (start-emotions percept-chan state-chan)
    ;; TODO block on a channel forever to keep running, need a proper shutdown mechanism
    (<!! (chan))))
