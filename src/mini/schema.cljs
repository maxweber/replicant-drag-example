(ns mini.schema)

(def schema
  {
   ;; ----------------------------------------------------------
   ;; Clips (represent a segment of an asset placed on a track)
   ;; ----------------------------------------------------------
   :clip/uuid
   {:db/unique :db.unique/identity
    :db/doc "Unique UUID for each clip on the timeline"}

   :clip/start
   {;; :db/valueType :db.type/long
    :db/doc "Start time on the timeline (in milliseconds)"}

   :clip/end
   {;; :db/valueType :db.type/long
    :db/doc "End time on the timeline (in milliseconds)"}

   :clip/track
   {:db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Which track this clip belongs to"}

   ;; ----------------------------------------------------------
   ;; Tracks (a collection of clips, transitions, etc.). On the UI it is a lane in the timeline.
   ;; ----------------------------------------------------------
   :track/uuid
   {:db/unique :db.unique/identity
    :db/doc    "Unique UUID for each track (audio track, video track, etc.)"}

   :track/name
   {;; :db/valueType :db.type/string
    :db/doc       "Optional name for the track (e.g., 'Video 1', 'Audio 1')"}

   })
