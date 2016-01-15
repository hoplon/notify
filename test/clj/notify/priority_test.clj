(ns notify.priority-testapi
  (:require [clojure.data.priority-map :refer :all]))

(def x "ab")
(def y (str "a" "b"))
(println (identical? x y)) ; -> false
(println (= x y)) ; -> true

(def p (priority-map-keyfn first x [2 0]))

(println (contains? p y)) ; -> true
