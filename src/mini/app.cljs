(ns mini.app
  (:require [clojure.walk :as walk]
            [gadget.inspector :as inspector]
            [replicant.dom :as r]
            [datascript.core :as ds]
            [mini.schema :as schema]
            ))

(defonce ^:private !state
  (atom {:pixel-per-second 100}))

(defn ms-to-pixel
  [pixel-per-second ms]
  (* (/ ms
        1000)
     pixel-per-second))

(defn pixel-to-ms
  [pixel-per-second pixel]
  (long (* (/ pixel
              pixel-per-second)
           1000)))

(defn q-track-uuids
  [db]
  (ds/q
   '[:find [?track-uuid ...]
     :where
     [?track :track/uuid ?track-uuid]]
   db))

(defn timeline [state]
  (for [track-uuid (q-track-uuids (:db state))]
    (let [track (ds/entity (:db state)
                           [:track/uuid track-uuid])]
      (for [clip (:clip/_track track)]
        (let [left (ms-to-pixel
                    (:pixel-per-second state)
                    (:clip/start clip))
              {:keys [clip/start
                      clip/end]} clip
              duration (- end
                          start)
              width (ms-to-pixel (:pixel-per-second state)
                                 duration)]
          [:div
           {:style {:position "relative"
                    :width "800px"
                    :height "204px"
                    :background-color "LightGray"
                    :margin-top "20px"}}
           [:div
            {:style {:position "absolute"
                     :left (str left
                                "px")
                     :width (str width "px")
                     :height "200px"
                     :display "flex"
                     :align-items "center"
                     :justify-content "center"
                     :border "2px solid BlanchedAlmond"
                     :background-color "beige"
                     :user-select "none"}
             :on {:mousedown [[:drag {:clip/uuid (:clip/uuid clip)
                                      :start-offset left}]]}}
            "drag me"]])))))

(defn- main-view [state]
  [:div {:style {:position "relative"}}
   [:h1 "A tiny drag example"]
   (timeline state)
   (pr-str state)])

(defn- enrich-action-from-event [{:replicant/keys [js-event node]} actions]
  (walk/postwalk
   (fn [x]
     (cond
       (keyword? x)
       (case x
         :event/target.value (-> js-event .-target .-value)
         :dom/node node
         x)
       :else x))
   actions))

(defn- enrich-action-from-state [state action]
  (walk/postwalk
   (fn [x]
     (cond
       (and (vector? x)
            (= :db/get (first x))) (get state (second x))
       :else x))
   action))

(defn- render! [state]
  (r/render
   (js/document.getElementById "app")
   (main-view state)))

(defn- event-handler [{:replicant/keys [^js js-event] :as replicant-data} actions]
  (doseq [action actions]
    (prn "Triggered action" action)
    (let [enriched-action (->> action
                               (enrich-action-from-event replicant-data)
                               (enrich-action-from-state @!state))
          [action-name & args] enriched-action]
      (prn "Enriched action" enriched-action)
      (case action-name
        :dom/prevent-default (.preventDefault js-event)
        :db/assoc (apply swap! !state assoc args)
        :db/dissoc (apply swap! !state dissoc args)
        :dom/set-input-text (set! (.-value (first args)) (second args))
        :dom/focus-element (.focus (first args))
        :drag (swap! !state assoc :drag (merge
                                         (first args)
                                         {:start-x (.-clientX js-event)}))
        (prn "Unknown action" action)))))

(defn on-mouse-move [evt]
  (prn "global: on-mouse-move")
  (let [state @!state]
    (when (:drag state)
      (let [{:keys [clip/uuid start-x start-offset]} (:drag state)
            current-x (.-clientX evt)
            delta-x   (- current-x start-x)
            new-left  (+ start-offset delta-x)
            pixel-per-second (:pixel-per-second state)
            new-start (pixel-to-ms pixel-per-second
                                   new-left)
            clip (ds/entity (:db state)
                            [:clip/uuid uuid])
            duration (- (:clip/end clip)
                        (:clip/start clip))
            new-end (+ new-start
                       duration)]
        (swap! !state
               update
               :db
               (fn [db]
                 (:db-after
                  (ds/with db
                           [{:clip/uuid uuid
                             :clip/start new-start
                             :clip/end new-end}]))))))))

(defn on-mouse-up
  [_event]
  (prn "global: on-mouse-up")
  (swap! !state
         dissoc
         :drag))

(defn register!
  []
  (.addEventListener js/document
                     "mousemove"
                     on-mouse-move)
  (.addEventListener js/document
                     "mouseup"
                     on-mouse-up))

(defn ^{:dev/after-load true :export true} start! []
  (render! @!state))

(def example-tx
  [{:clip/uuid #uuid "aa444392-b13a-4e03-8718-9e33b679cdb1"
    :clip/start 2000
    :clip/end 5000
    :clip/track "video-track"}

   {:clip/uuid #uuid "446cf728-0725-49d8-ab71-4db969cfe3a2"
    :clip/start 3000
    :clip/end 4000
    :clip/track "audio-track"}

   {:db/id "video-track"
    :track/uuid #uuid "0224ce8f-7a7d-4a93-b6e8-99b0a8e4f7e7"
    :track/name "main track"}

   {:db/id "audio-track"
    :track/uuid #uuid "961a8385-b527-4027-9320-a7ce4dd412fd"
    :track/name "audio track"}
   ])

(defn ^:export init! []
  (swap! !state
         assoc
         :db
         (:db-after
          (ds/with (ds/empty-db schema/schema)
                   example-tx)))
  (inspector/inspect "App state" !state)
  (r/set-dispatch! #'event-handler)
  (register!)
  (add-watch
   !state
   ::render
   (fn [_ _ _ state]
     (render! state)))
  (start!))
