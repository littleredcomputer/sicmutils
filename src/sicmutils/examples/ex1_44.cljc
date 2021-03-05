;;
;; Copyright © 2017 Colin Smith.
;; This work is based on the Scmutils system of MIT/GNU Scheme:
;; Copyright © 2002 Massachusetts Institute of Technology
;;
;; This is free software;  you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3 of the License, or (at
;; your option) any later version.
;;
;; This software is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with this code; if not, see <http://www.gnu.org/licenses/>.
;;

(ns sicmutils.examples.ex1-44
  (:refer-clojure :exclude [+ - * /])
  (:require [sicmutils.env :as e
             :refer [compose up down square sin cos + - * /]]))

(defn angles->rect [l1 l2]
  (fn [[t [theta1 theta2]]]
    (let [x1 (* l1 (sin theta1))
	        y1 (- (* l1 (cos theta1)))
          x2 (+ x1 (* l2 (sin (+ theta1 theta2))))
	        y2 (- y1 (* l2 (cos (+ theta1 theta2))))]
	    (up x1 y1 x2 y2))))

(defn T [m1 m2]
  (fn [[_ _ [xdot1 ydot1 xdot2 ydot2]]]
    (+ (* (/ 1 2) m1 (+ (square xdot1) (square ydot1)))
	     (* (/ 1 2) m2 (+ (square xdot2) (square ydot2))))))

(defn V [m1 m2 g]
  (fn [[_ [_ y1 _ y2]]]
    (+ (* m1 g y1)
       (* m2 g y2))))

(defn L-rect [m1 m2 g]
  (- (T m1 m2)
     (V m1 m2 g)))

(defn L-double-pendulum [m1 m2 l1 l2 g]
  (compose (L-rect m1 m2 g)
           (e/F->C
            (angles->rect l1 l2))))

(def L-energy
  (compose
   e/Lagrangian->energy
   L-double-pendulum))

(comment
  (is (= '(+ (* l_1 l_2 m_2 (expt theta_1dot 2) (cos theta↑1) (cos (+ theta↑1 theta↑2)))
             (* l_1 l_2 m_2 (expt theta_1dot 2) (sin theta↑1) (sin (+ theta↑1 theta↑2)))
             (* l_1 l_2 m_2 theta_1dot theta_2dot (cos theta↑1) (cos (+ theta↑1 theta↑2)))
             (* l_1 l_2 m_2 theta_1dot theta_2dot (sin theta↑1) (sin (+ theta↑1 theta↑2)))
             (* (/ 1 2) (expt l_1 2) m_1 (expt theta_1dot 2))
             (* (/ 1 2) (expt l_1 2) m_2 (expt theta_1dot 2))
             (* (/ 1 2) (expt l_2 2) m_2 (expt theta_1dot 2))
             (* (expt l_2 2) m_2 theta_1dot theta_2dot)
             (* (/ 1 2) (expt l_2 2) m_2 (expt theta_2dot 2))
             (* g l_1 m_1 (cos theta↑1))
             (* g l_1 m_2 (cos theta↑1))
             (* g l_2 m_2 (cos (+ theta↑1 theta↑2))))
         (e/freeze
          (e/simplify
           ((L-double-pendulum 'm_1 'm_2 'l_1 'l_2 'g)
            (up 't (up 'theta↑1 'theta↑2) (up 'theta_1dot 'theta_2dot))))))))

(def state-derivative
  (compose
   e/Lagrangian->state-derivative
   L-double-pendulum))

(comment
  (define w1 (frame 0 50 -pi pi))
  (define w2 (frame 0 50 -pi pi))
  (define ewin (frame 0 50 -1e-10 1e-10))
  (rename-window w1 "Theta-1")
  (rename-window w2 "Theta-2")
  (rename-window ewin "Energy Error"))

(declare w1 w2 ewin)

(defn graphics-clear [window])
(defn graphics-close [window])
(defn plot-point [window x y])

(defn clear! []
  (graphics-clear w1)
  (graphics-clear w2)
  (graphics-clear ewin))

(defn close! []
  (graphics-close w1)
  (graphics-close w2)
  (graphics-close ewin))

(defn energy-monitor [window energy-fn initial-state]
  (let [initial-energy (energy-fn initial-state)]
    (fn [state]
      (let [t (e/state->t state)
            e (- (energy-fn state) initial-energy)]
        (plot-point window t e)
        state))))

(defn angle-monitor [window i]
  (let [pv (e/principal-value Math/PI)]
    (fn [state]
      (let [t     (e/state->t state)
            theta (pv (get-in state [1 i]))]
        (plot-point window t theta)
        state))))

(defn chaotic-initial-condition []
  (let [m1 1.0 m2 3.0
        l1 1.0 l2 0.9
        g 9.8
        initial-state (up 0.0
                          (up (/ Math/PI 2) Math/PI)
                          (up 0.0 0.0))
        energy-fn (L-energy m1 m2 l1 l2 g)]
    (clear!)
    ((e/evolve state-derivative m1 m2 l1 l2 g)
     initial-state
     0.01
     50
     {:compile? true
      :epsilon 1.0e-13 ; = (max-norm 1.e-13)
      :observe
      (comp (angle-monitor w1 0)
	          (angle-monitor w2 1)
	          (energy-monitor ewin energy-fn initial-state)
            (fn [t state] state))})))

(defn regular-initial-condition []
  (let [m1 1.0 m2 3.0
        l1 1.0 l2 0.9
        g 9.8
        initial-state (up 0.0
                          (up (/ Math/PI 2) 0)
                          (up 0.0 0.0))
        energy-fn (L-energy m1 m2 l1 l2 g)]
    (clear!)
    ((e/evolve state-derivative m1 m2 l1 l2 g)
     initial-state
     0.01
     50
     {:compile? true
      :epsilon 1.0e-13 ; = (max-norm 1.e-13)
      :observe
      (comp (angle-monitor w1 0)
	          (angle-monitor w2 1)
	          (energy-monitor ewin energy-fn initial-state)
            (fn [t state] state))})))

(comment
  ;; matches GJS:
  (up 50.00000000000269
      (up 1.0874071859820953 0.5151887602034145)
      (up 1.9461351773576496 -0.42058061008304115)))

(defn L-double-double-pendulum [m1 m2 l1 l2 g]
  (fn [[t [thetas1 thetas2] [qdots1 qdots2]]]
    (let [s1 (up t thetas1 qdots1)
	        s2 (up t thetas2 qdots2)]
	    (+ ((L-double-pendulum m1 m2 l1 l2 g) s1)
	       ((L-double-pendulum m1 m2 l1 l2 g) s2)))))

(def dd-state-derivative
  (compose
   e/Lagrangian->state-derivative
   L-double-double-pendulum))

(defn safe-log [x]
  (if (< x 1e-60)
    -138.0
    (Math/log x)))

(defn divergence-monitor [window]
  (let [pv (e/principal-value Math/PI)]
    (fn [[t [thetas1 thetas2] :as state]]
      (let [v (safe-log
               (Math/abs
                (pv
				         (- (nth thetas1 1)
				            (nth thetas2 1)))))]
        (plot-point window t v)
        state))))

(defn run-double-double
  "Two different initializations, slightly kicked"
  ([initial-q1]
   (run-double-double initial-q1 w1))
  ([initial-q1 window]
   (let [m1 1.0 m2 3.0
         l1 1.0 l2 0.9
         g 9.8
         initial-q2    (+ initial-q1 (up 0.0 1e-10))
         initial-qdot  (up 0.0 0.0)
         initial-state (up 0.0
		                       (up initial-q1 initial-q2)
		                       (up initial-qdot initial-qdot))]
     (graphics-clear window)
     ((e/evolve dd-state-derivative m1 m2 l1 l2 g)
      initial-state
      0.01
      50
      {:compile? true
       :epsilon 1.0e-13 ; = (max-norm 1.e-13)
       :observe
       (comp (divergence-monitor window)
             (fn [t state] state))}))))

(comment
  "regular:"
  (run-double-double (up (/ Math/PI 2) 0))

  "Chaotic:"
  (run-double-double (up (/ Math/PI 2) Math/PI)))
