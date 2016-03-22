;; quick and dirty naojure replacement using Aldebaran Java SDK
(ns emotions-demo.nao
  (:require [clojure.core.async :as async
             :refer [<! >! <!! timeout chan alts! alts!! put! go]])
  (:import [com.aldebaran.qi Application]
           [com.aldebaran.qi.helper.proxies ALAudioPlayer ALBacklightingDetection ALBarcodeReader ALBattery ALMemory ALTextToSpeech ALBasicAwareness ALDarknessDetection ALMotion ALBehaviorManager ALRobotPosture ALSpeechRecognition ALLeds ALNavigation ALPhotoCapture ALPreferences ALSensors ALSonar ALSoundDetection ALSpeechRecognition]
           [com.aldebaran.qi.helper EventCallback]))

(def proxy-names {
                  :audio-player ALAudioPlayer
                  :awareness ALBasicAwareness
                  :backlight-detection ALBacklightingDetection
                  :barcode ALBarcodeReader
                  :battery ALBattery
                  :behaviour-manager ALBehaviorManager
                  :darkness-detection ALDarknessDetection
                  :leds ALLeds
                  :memory ALMemory
                  :motion ALMotion
                  :navigation ALNavigation
                  :photo-capture ALPhotoCapture
                  :posture ALRobotPosture
                  :preferences ALPreferences
                  :sensors ALSensors
                  :sonar ALSonar
                  :sound-detection ALSoundDetection
                  :speech-recognition ALSpeechRecognition
                  :tts ALTextToSpeech
                  })


(def subscriptions (atom []))

(def robot-atom (atom {}))

(def proxies (atom {}))

(defn get-session
  []
  (.session (@robot-atom :app)))

(defn new-proxy
  [name-sym]
  (clojure.lang.Reflector/invokeConstructor (proxy-names name-sym) (into-array Object [(get-session)])))

(defn get-proxy
  [name-sym]
  (let [e (@proxies name-sym)]
    (if e e
        (name-sym (swap! proxies assoc name-sym (new-proxy name-sym))))))

(defn add-subscription
  [id]
  (swap! subscriptions conj id))

(defn make-callback
  [callback]
  (reify
    EventCallback
    (onEvent [this param] (callback param))))

(defn make-callback-chan
  [event-name event-chan]
  (reify
    EventCallback
    (onEvent [this param] (put! event-chan [event-name param]))))

(defn get-memory-value
  [key]
  (.getData (get-proxy :memory) key))

(defn set-memory-value
  [key value]
  (.insertData (get-proxy :memory) key value))


(defn run-behaviour
  ([behaviour-name]
   (if behaviour-name
     (.runBehavior (get-proxy :behaviour-manager) behaviour-name)))
  ;; implement properly with callbacks and async proxy when time
  ([behaviour-name complete-chan]
   (if behaviour-name
     (do
       (run-behaviour behaviour-name)
       (put! complete-chan true))))
  )

;; TODO implement "safe say" which disables speech recognition while robot is talking

(defn say
  ([text]
   (.say (get-proxy :tts) text))
  ;; implement properly with callbacks and async proxy when time
  ([text complete-chan]
   (do
     (say text)
     (put! complete-chan true)))
  )

(defn add-event-chan
  [event-name event-chan]
  (add-subscription (.subscribeToEvent (get-proxy :memory) event-name
                                       (make-callback-chan event-name event-chan))))
(defn set-volume
  [volume]
  (.setVolume (get-proxy :tts) (float volume)))
  
(defn get-human-list
  []
  (.getData (get-proxy :memory) "PeoplePerception/PeopleList"))

(defn people-count
  []
  (count (get-human-list)))

(defn robot-standing?
  []
  (= "Standing" (.getPostureFamily (get-proxy :posture))))

(defn robot-sitting?
  []
  (= "Sitting" (.getPostureFamily (get-proxy :posture))))

(defn connect
  [ip-addr]
  (let [app (Application. (into-array String [])
                          (str "tcp://" ip-addr ":9559"))]
    (swap! robot-atom assoc :app app)
    (.start app)))

(defn connected?
  []
  (contains? @robot-atom :app))

(defn unsubscribe
  []
  ;; this would be the preferred way to do it but, as of 2.1.4.13, fails with:
  ;; ConcurrentModificationException   java.util.HashMap$HashIterator.nextNode (HashMap.java:1429
  ;;(.unsubscribeAllEvents memory)
  (doseq [id @subscriptions] (.unsubscribeToEvent (get-proxy :memory) id)))

(defn wake-up
  []
  (do
    (.wakeUp (get-proxy :motion))
    (.setEngagementMode (get-proxy :awareness) "FullyEngaged")
    (.setTrackingMode (get-proxy :awareness) "Head")
    (.setStimulusDetectionEnabled (get-proxy :awareness) "Sound" true)
    (.setStimulusDetectionEnabled (get-proxy :awareness) "Movement" true)
    (.setStimulusDetectionEnabled (get-proxy :awareness) "People" true)
    (.setStimulusDetectionEnabled (get-proxy :awareness) "Touch" true)
    (.startAwareness (get-proxy :awareness))
    ))

(defn start-listening
  [word-list]
  (let [sr (get-proxy :speech-recognition)]
    (.setVocabulary sr (java.util.ArrayList. word-list) true)
    ;; turn off the eye effects and audio "beep" when NAO is listening
    (.setAudioExpression sr false)
    (.setVisualExpression sr false)
    ;; turn on speech recognition
    (.subscribe sr "NAO")
    ))

(defn stop-listening
  []
  (.unsubscribe (get-proxy :speech-recognition) "NAO"))
