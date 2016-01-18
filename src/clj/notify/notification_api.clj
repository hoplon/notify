(ns notify.notification-api
  (:require [castra.core :refer [defrpc *session*]]
            [clojure.data.priority-map :refer [priority-map-keyfn]])
  (:import [java.util UUID Comparator]))

;default decay min=200, max=3000, inc=100 (milliseconds)
(defonce decay (atom [200 3000 100]))
(defn get-decay
  "Returns the decay spec vector [min, max, inc]"
  [] @decay)
(defn set-decay! [min max inc]
  (reset! decay [min max inc]))

(defonce max-sessions (atom 1000))
(defn get-max-sessions
  "Returns the maximum number of sessions.
  When exceeded, the session which has not polled for the longest period is dropped."
  [] @max-sessions)
(defn set-max-sessions!
  "Sets the maximum number of sessions."
  [new-max] (reset! max-sessions new-max))

(defonce aged-sessions (atom (priority-map-keyfn first)))
(defn get-session-count
  "Returns the number of sessions"
  [] (count @aged-sessions))

(defn add-session [pm session-id timestamp ack notifications]
  (assoc pm session-id [timestamp ack notifications]))
(defn get-timestamp [pm session-id]
  (get-in pm [session-id 0]))
(defn assoc-timestamp [pm session-id timestamp]
  (assoc-in pm [session-id 0] timestamp))
(defn get-last-sent [pm session-id]
  (get-in pm [session-id 1]))
(defn assoc-last-sent [pm session-id ack]
  (assoc-in pm [session-id 1] ack))
(defn get-unacked-notifications [pm session-id]
  (get-in pm [session-id 2]))
(defn assoc-unacked-notifications [pm session-id notifications]
  (assoc-in pm [session-id 2] notifications))

(defn add-notification* [pm session-id type value]
  (let [last-sent (+ 1 (get-last-sent pm session-id))
        pm (assoc-last-sent pm session-id last-sent)
        unacked-sesion-notifications (get-unacked-notifications pm session-id)
        unacked-sesion-notifications (assoc
                                       unacked-sesion-notifications
                                       last-sent
                                       {:notification-type type
                                        :value value
                                        :timestamp (System/currentTimeMillis)})
        pm (assoc-unacked-notifications pm session-id unacked-sesion-notifications)]
    pm))

(defn add-notification!
  "Adds a notification [type value] to the list of notifications to be sent to a given session.
   Notification identifiers are assigned in ascending order on a per session basis."
  [session-id type value]
  (swap! aged-sessions add-notification* session-id type value))

(defn drop-acked-notifications
  "Remove all notifications with an ack <= last-ack"
  [pm session-id last-ack]
    (let [unacked-sesion-notifications (get-unacked-notifications pm session-id)
          unacked-sesion-notifications (reduce
                                         (fn [m k]
                                           (if (> k last-ack)
                                             m
                                             (dissoc m k)))
                                         unacked-sesion-notifications
                                         (keys unacked-sesion-notifications))
          pm (assoc-unacked-notifications pm session-id unacked-sesion-notifications)]
      pm))

(defn get-session-id
  "Returns the session id (a UUID string) assigned to the current session."
  [] (:session-id @*session*))

(defn identify-session!
  "Assign a unique random identifier (a UUID) to the current session, as needed.
   Returns true."
  []
  (if (nil? (:session-id @*session*))
    (swap! *session* assoc :session-id (.toString (UUID/randomUUID))))
  true)

(defn make-session! [last-ack session-id]
  (swap!
    aged-sessions
    (fn [pm]
      (let [pm (if (get pm session-id)
                 pm
                 (let [pm (add-session pm session-id (System/currentTimeMillis) last-ack {})
                       pm (add-notification* pm session-id :new-session "")]
                   pm))
            pm (if (>= (get-max-sessions) (count pm))
                 pm
                 (do
                   (pop pm)))]
      pm))))

(defn update-on-ack! [last-ack session-id]
  (swap!
    aged-sessions
    (fn [pm]
      (let [pm (assoc-timestamp pm session-id (System/currentTimeMillis))
            pm (drop-acked-notifications pm session-id last-ack)]
        pm))))

(defrpc get-notifications
        "An rpc call to return all the new notifications for the current session."
        [last-ack & [session-id]]
        {:rpc/pre [(nil? session-id) (identify-session!)]}
        (let [session-id (or session-id (get-session-id))]
          (make-session! last-ack session-id)
          (update-on-ack! last-ack session-id)
          (get-unacked-notifications @aged-sessions session-id)))

(defrpc smart-get-notifications
        "Check the decay specs, add a notification if needed, and then
        return the unacked notifications."
        [decay-specs last-ack & session-id]
        {:rpc/pre [(nil? session-id) (identify-session!)]}
        (let [session-id (or session-id (get-session-id))]
          (make-session! last-ack session-id)
          (update-on-ack! last-ack session-id)
          (if (not= decay-specs @decay)
            (add-notification! session-id :decay @decay))
          (get-unacked-notifications @aged-sessions session-id)))
