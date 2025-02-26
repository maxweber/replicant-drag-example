(ns mini.app
  (:require [clojure.walk :as walk]
            [gadget.inspector :as inspector]
            [replicant.dom :as r]))

(defonce ^:private !state (atom {:left 100}))

(defn- display-view [state]
  [:div
   {:style {:position "relative"
            :width "800px"
            :height "204px"
            :background-color "LightGray"}}
   [:div
    {:style {:position "absolute"
             :left (str (:left state)
                        "px")
             :width "200px"
             :height "200px"
             :display "flex"
             :align-items "center"
             :justify-content "center"
             :border "2px solid BlanchedAlmond"
             :background-color "beige"
             :user-select "none"}
     :on {:mousedown [[:drag]]}}
    "drag me"]])

(defn- main-view [state]
  [:div {:style {:position "relative"}}
   [:h1 "A tiny drag example"]
   (display-view state)
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
          [action-name & args] enriched-action
          state @!state]
      (prn "Enriched action" enriched-action)
      (case action-name
        :dom/prevent-default (.preventDefault js-event)
        :db/assoc (apply swap! !state assoc args)
        :db/dissoc (apply swap! !state dissoc args)
        :dom/set-input-text (set! (.-value (first args)) (second args))
        :dom/focus-element (.focus (first args))
        :drag (swap! !state assoc :drag {:start-offset (:left state)
                                         :start-x (.-clientX js-event)})
        (prn "Unknown action" action)))))

(defn on-mouse-move [evt]
  (prn "global: on-mouse-move")
  (when (:drag @!state)
    (let [{:keys [start-x start-offset]} (:drag @!state)
          current-x (.-clientX evt)
          delta-x   (- current-x start-x)
          new-left  (+ start-offset delta-x)]
      (swap! !state
             assoc
             :left
             new-left))))

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

(defn ^:export init! []
  (inspector/inspect "App state" !state)
  (r/set-dispatch! #'event-handler)
  (register!)
  (add-watch
   !state
   ::render
   (fn [_ _ _ state]
     (render! state)))
  (start!))
