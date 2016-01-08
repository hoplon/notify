(ns notify.notification-api
  (:require [castra.core :refer [defrpc *session*]]
            [clojure.data.priority-map :refer :all])
  (:import [java.util UUID Comparator]))

(defonce max-sessions (atom 1000))
(defn get-max-sessions [] @max-sessions)
(defn set-max-sessions! [new-max] (reset! max-sessions new-max))

(defonce aged-sessions (atom (priority-map-keyfn first)))

(defn add-session [pm session-id timestamp ack notifications]
  (assoc pm session-id [timestamp ack notifications]))
(defn sessions-count [] (count @aged-sessions))
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

(defn add-notification!
  "Adds a notification [type value] to the list of notifications to be sent to a given session.
   Notification identifiers are assigned in ascending order on a per session basis."
  [session-id type value]
  (swap!
    aged-sessions
    (fn [pm]
      (if (contains? pm session-id)
        (let [last-sent (+ 1 (get-last-sent pm session-id))
              pm (assoc-last-sent pm session-id last-sent)
              unacked-sesion-notifications (get-unacked-notifications pm session-id)
              unacked-sesion-notifications (assoc
                                             unacked-sesion-notifications
                                             last-sent
                                             {:notification-type type :value value})
              pm (assoc-unacked-notifications pm session-id unacked-sesion-notifications)]
          pm)
        (let [pm (add-session pm session-id (System/currentTimeMillis) 0
                              {1 {:notification-type type :value value}})
              pm (assoc-last-sent pm session-id 1)]
          pm)))))

(defn drop-acked-notifications
  "Remove all notifications with an ack <= last-ack"
  [pm session-id last-ack]
  (if (contains? pm session-id)
    (let [unacked-sesion-notifications (get-unacked-notifications pm session-id)
          unacked-sesion-notifications (reduce
                                         (fn [m k]
                                           (if (> k last-ack)
                                             m
                                             (dissoc m k)))
                                         unacked-sesion-notifications
                                         (keys unacked-sesion-notifications))
          pm (assoc-unacked-notifications pm session-id unacked-sesion-notifications)]
      pm)
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

(defrpc get-notifications
        "An rpc call to return all the new notifications for the current session."
        [last-ack & [session-id]]
        {:rpc/pre [(nil? session-id) (identify-session!)]}
        (let [session-id (or session-id (get-session-id))
              timestamp (System/currentTimeMillis)]
          (swap!
            aged-sessions
            (fn [pm]
              (if (contains? pm session-id)
                (let [pm (assoc-timestamp pm session-id timestamp)
                      pm (drop-acked-notifications pm session-id last-ack)]
                  pm)
                (add-session pm session-id timestamp last-ack {}))))
          (get-unacked-notifications @aged-sessions session-id)))
