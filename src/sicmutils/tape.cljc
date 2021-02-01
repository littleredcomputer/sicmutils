;;
;; Copyright © 2020 Sam Ritchie.
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

(ns sicmutils.tape
  "Basic machinery for reverse-mode AD! Let's see how far we get."
  (:require [sicmutils.differential :as d]
            [sicmutils.function :as f]
            [sicmutils.generic :as g]
            [sicmutils.structure :as s]
            [sicmutils.util :as u]
            [sicmutils.util.stream :as us]
            [sicmutils.value :as v])
  #?(:clj
     (:import (clojure.lang MultiFn))))

;; Start with a port of the DVL implementation.

(derive ::tape ::f/cofunction)

(deftype TapeCell [tag id primal in->partial]
  v/Value
  (zero? [this] (v/zero? primal))

  (one? [this] (and (v/one? primal)
                    (empty? in->partial)))
  (identity? [this] (and (v/identity? primal)
                         (empty? in->partial)))

  (zero-like [_] 0)
  (one-like [_] 1)
  (identity-like [_] 1)
  (freeze [_] `[~'TapeCell ~tag ~id ~(v/freeze primal)
                ~(mapv (fn [[k v]] [(v/freeze k) (v/freeze v)])
                       in->partial)])
  (exact? [_] false)

  (kind [_] ::tape))

;; These come from stdlib/ad-structures.dvl.

(defn tape-tag [^TapeCell tape] (.-tag tape))
(defn tape-id [^TapeCell tape] (.-id tape))
(defn tape-primal [^TapeCell tape] (.-primal tape))
(defn tape-partials [^TapeCell tape] (.-in->partial tape))

;; TODO do we want the "epsilon" or "tag" to be a protocol? no way...

(defn tape? [x]
  (instance? TapeCell x))

(defn make
  "Make a `TapeCell` instance with a unique ID."
  ([tag primal]
   (make tag primal []))
  ([tag primal partials]
   (->TapeCell tag (gensym) primal partials)))

(defn primal-part
  "TODO THIS maybe could be a protocol if indeed the other implementations fall
  down with it. This is a flag about how we're supposed to respond when FORWARD
  mode tries to get our primal - do not give it up!!

  NOTE from dvl... This expects that primal is always called with the outermost
  available epsilon, and that bundles and tape cells are properly nested."
  [x tag]
  x)

(defn- reverse-primal
  "This assumes that epsilon is greater than or equal to the perturbation of
  thing, and is an epsilon associated with reverse mode."
  [x tag]
  (if (and (tape? x) (= tag (tape-tag x)))
    (tape-primal x)
    x))

(defn primal*
  "TODO this is also in ad-structures. This goes ahead and descends deep and
  returns the ACTUAL primal part of either the tape or the differential... or ID
  if it's neither of these container types."
  [thing]
  (if (tape? thing)
    (primal* thing)
    thing))

;; TODO BOOM we actually... do we need to be more careful about extracting the
;; appropriate primal part? I think the deal is that we get primals out of
;; bundles, and just straight-up return them if they are tapes.

(defn tangent-part
  "TODO wtf so weird, we ALSO only return tangent part here... oh. That is
  probably because with proper nesting, if we're on the OUTSIDE then..."
  [x tag] 0)

(defn tag-of
  "TODO redo this and make it so it doesn't HAVE to have a tag; but right now
  we're returning 0 since it'll be less than all tags.

  NOTE this is sort of like max-order-tag; but everything is not flattened out."
  [x]
  (if (tape? x)
    (tape-tag x)
    0))

(comment
  ;; ALSO in primal-part we have this tag hiding thing. So looks like that does
  ;; not have to change.
  (hide-gensym-in-procedure epsilon
                            (lambda (x)
                                    (primal epsilon (thing x)))))

(defprotocol ITapify
  (-tapify [this tag]))

#_
(comment
  "TODO Do this later!"
  (extend-protocol ITapify ...))

(defn tapify
  "Down to business! This should be a protocol method."
  [x tag]
  (cond (v/scalar? x)   (make tag x [])

        (s/structure? x) (s/mapr #(tapify % tag) x)
        (f/function? x)  (u/illegal "Can't do this yet.")

        :else x))

(defn reverse-phase
  "sensitivities is a map... nodes is a sequence."
  [nodes sensitivities]
  (if (empty? nodes)
    sensitivities
    ;; Since you're going in topological sort order, when you reach
    ;; a node you know you are done updating its sensitivity.
    (let [sensitivity (get sensitivities (tape-id (first nodes)))]
      (loop [sensitivities sensitivities
             partials      (tape-partials (first nodes))]
        (if (empty? partials)
          (reverse-phase (rest nodes) sensitivities)
          (let [[[partial-cell partial-factor] & rest] partials]
            (recur (update sensitivities
                           (tape-id partial-cell)
                           g/+
                           (g/* partial-factor sensitivity))
                   rest)))))))

(declare compute-visiting-order*)

(defn compute-visiting-order [node seen sorted]
  (if (contains? seen (tape-id node))
    [seen sorted]
    (let [[seen sorted] (compute-visiting-order*
                         (map first (tape-partials node))
                         (conj seen (tape-id node))
                         sorted)]
      [seen (cons node sorted)])))

(defn compute-visiting-order* [nodes seen sorted]
  (if (empty? nodes)
    [seen sorted]
    (let [[seen sorted] (compute-visiting-order
                         (first nodes) seen sorted)]
      (compute-visiting-order* (rest nodes) seen sorted))))

(defn interpret [thing tag sensitivities]
  (cond (and (tape? thing)
             (= tag (tape-tag thing)))
        (get sensitivities (tape-id thing))

        (s/structure? thing)
        (s/opposite
         thing (map #(interpret % tag sensitivities) thing))

        (f/function? thing)  (u/illegal "function return not yet supported.")
        :else 0))

(defn gradient
  "Returns an function that returns the gradient of the supplied fn... still quite
  simplified."
  [f]
  (fn [x]
    (let [tag    (d/fresh-tag)
          inputs (tapify x tag)
          fwd    (f inputs)
          sensitivities (if (tape? fwd)
                          (if (= tag (tape-tag fwd))
                            (let [[seen sorted] (compute-visiting-order fwd #{} [])]
                              (reverse-phase sorted {(tape-id fwd) 1}))
                            ;; f is not infinitesimally dependent on x, return
                            ;; the empty sensitivity list.
                            {})
                          {})]
      (interpret inputs tag sensitivities))))

;; ## Lifted Fns
;;
;; These are all almost totally duplicated from the derivatives we have in
;; `generic`. I wonder if it would make sense to attach the derivatives as
;; metadata to the original functions. We have the machinery now to do this...

(defn lift-1
  "As with differential, `df:dx` has to have ALREADY been lifted here."
  [f df:dx]
  (fn call [x]
    (if (tape? x)
      (let [primal (tape-primal x)]
        (make (tape-tag x)
              (call primal)
              [[x (df:dx primal)]]))
      (f x))))

(defn lift-2 [f df:dx1 df:dx2]
  (fn call [a b]
    (letfn [(operate [tag]
              (let [prim-a (reverse-primal a tag)
                    prim-b (reverse-primal b tag)]
                (let [partial-a (if (and (tape? a)
                                         (= tag (tape-tag a)))
                                  [[a (df:dx1 prim-a prim-b)]]
                                  [])
                      partial-b (if (and (tape? b)
                                         (= tag (tape-tag b)))
                                  [[b (df:dx2 prim-a prim-b)]]
                                  [])]
                  (make tag
                        (call prim-a prim-b)
                        (concat partial-a partial-b)))))]
      (let [tag-a (tag-of a)
            tag-b (tag-of b)]
        (cond (and (tape? a) (not (< tag-a tag-b)))
              (operate tag-a)

              (and (tape? b) (< tag-a tag-b))
              (operate tag-b)

              :else (f a b))))))

(defn- defunary [generic-op differential-op]
  (defmethod generic-op [::tape] [a] (differential-op a)))

(defn- defbinary [generic-op differential-op]
  (doseq [signature [[::tape ::tape]
                     [::v/scalar ::tape]
                     [::tape ::v/scalar]]]
    (defmethod generic-op signature [a b] (differential-op a b))))

(defbinary g/add
  (lift-2 g/add (g/add :dfdx) (g/add :dfdy)))

(defunary g/negate
  (lift-1 g/negate (g/negate :dfdx)))

(defunary g/negative?
  (fn [x] (g/negative? (primal* x))))

(defbinary g/sub
  (lift-2 g/sub (g/sub :dfdx) (g/sub :dfdy)))

(let [mul (lift-2
           g/mul
           (fn [_ y] y)
           (fn [x _] x))]
  (defbinary g/mul mul)
  (defunary g/square (fn [x] (mul x x)))
  (defunary g/cube (fn [x] (mul x (mul x x))))
  (defbinary g/dot-product mul))

(defunary g/invert
  (lift-1 g/invert
          (fn [x] (g/div -1 (g/square x)))))

(defbinary g/div
  (lift-2 g/div
          (fn [_ y] (g/div 1 y))
          (fn [x y] (g/div (g/negate x)
                          (g/square y)))))

(defunary g/abs
  (fn [x]
    (let [f (primal* x)
          func (cond (< f 0) (lift-1 (fn [x] x) (fn [_] -1))
                     (> f 0) (lift-1 (fn [x] x) (fn [_] 1))
                     (= f 0) (u/illegal "Derivative of g/abs undefined at zero")
                     :else (u/illegal (str "error! derivative of g/abs at" x)))]
      (func x))))

(defunary g/sqrt
  (lift-1 g/sqrt
          (fn [x]
            (g/invert
             (g/mul (g/sqrt x) 2)))))

(let [expt (lift-2
            g/expt
            (fn [x y]
              (g/mul y (g/expt x (g/sub y 1))))
            (fn [x y]
              (if (and (v/number? x) (v/zero? y))
                (if (v/number? y)
                  (if (not (g/negative? y))
                    0
                    (u/illegal "Derivative undefined: expt"))
                  0)
                (g/* (g/log x) (g/expt x y)))))]
  (defmethod g/expt [::tape ::tape] [d n] (expt d n))
  (defmethod g/expt [::v/scalar ::tape] [d n] (expt d n)))

;; TODO note the simpler case here.
(let [power (lift-2
             g/expt
             (fn [x y]
               (g/mul y (g/expt x (g/sub y 1))))
             (fn [_ _]
               (u/illegal "can't get there from here")))]
  (defmethod g/expt [::tape ::v/scalar] [d n] (power d n)))

(defunary g/log
  (lift-1 g/log g/invert))

(defunary g/exp
  (lift-1 g/exp g/exp))

(defunary g/sin
  (lift-1 g/sin g/cos))

(defunary g/cos
  (lift-1 g/cos
          (fn [x] (g/negate (g/sin x)))))

(defunary g/tan
  (lift-1 g/tan
          (fn [x]
            (g/invert
             (g/square (g/cos x))))))

(defunary g/asin
  (lift-1 g/asin
          (fn [x]
            (g/invert
             (g/sqrt (g/sub 1 (g/square x)))))))

(defunary g/acos
  (lift-1 g/acos
          (fn [x]
            (g/negate
             (g/invert
              (g/sqrt (g/sub 1 (g/square x))))))))

(defunary g/atan
  (lift-1 g/atan (fn [x]
                   (g/invert
                    (g/add 1 (g/square x))))))

(defbinary g/atan
  (lift-2 g/atan
          (fn [y x]
            (g/div x (g/add (g/square x)
                            (g/square y))))
          (fn [y x]
            (g/div (g/negate y)
                   (g/add (g/square x)
                          (g/square y))))))

(defunary g/sinh
  (lift-1 g/sinh g/cosh))

(defunary g/cosh
  (lift-1 g/cosh g/sinh))

(defunary g/tanh
  (lift-1 g/tanh
          (fn [x]
            (g/sub 1 (g/square (g/tanh x))))))
