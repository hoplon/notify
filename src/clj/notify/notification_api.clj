(ns notify.notification-api
  (:require [castra.core :refer [defrpc *session*]])
  (:import [java.util UUID]))

(defonce unacked-notifications (atom {}))

(defn add-notification!
  "Adds a notification [type value] to the list of notifications to be sent to a given session.
   Notification identifiers are assigned in ascending order on a per session basis."
  [session-id type value]
  (swap!
    unacked-notifications
    (fn [old]
      (let [unacked-sesion-notifications (get old session-id)
            unacked-sesion-notifications (if (nil? unacked-sesion-notifications)
                                           {:last-id 0 :notifications {}}
                                           unacked-sesion-notifications)
            new-id (+ 1 (:last-id unacked-sesion-notifications))
            unacked-sesion-notifications (assoc unacked-sesion-notifications :last-id new-id)
            notifications (assoc
                            (:notifications unacked-sesion-notifications)
                            new-id
                            {:notification-type type :value value})
            unacked-sesion-notifications (assoc
                                           unacked-sesion-notifications
                                           :notifications
                                           notifications)]
        (assoc old session-id unacked-sesion-notifications)))))

(defn drop-acked-notifications!
  "Remove all notifications with an id <= last-id"
  [session-id last-id]
  (swap!
    unacked-notifications
    (fn [old]
      (let [unacked-sesion-notifications (get old session-id)
            unacked-sesion-notifications (if (nil? unacked-sesion-notifications)
                                           {:last-id 0 :notifications {}}
                                           unacked-sesion-notifications)
            notifications (:notifications unacked-sesion-notifications)
            notifications (reduce
                            (fn [m k]
                              (if (> k last-id)
                                m
                                (dissoc m k)))
                            notifications
                            (keys notifications))
            unacked-sesion-notifications (assoc
                                           unacked-sesion-notifications
                                           :notifications
                                           notifications)]
        (assoc old session-id unacked-sesion-notifications)))))

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
        [last-id & [session-id]]
        {:rpc/pre [(nil? session-id) (identify-session!)]}
        (let [session-id (or session-id (get-session-id))
              _ (if (< 0 last-id)
                  (drop-acked-notifications! session-id last-id))
              unacked-sesion-notifications (get @unacked-notifications session-id)
              unacked-sesion-notifications (if (nil? unacked-sesion-notifications)
                                             {:last-id 0 :notifications {}}
                                             unacked-sesion-notifications)]
          unacked-sesion-notifications))
