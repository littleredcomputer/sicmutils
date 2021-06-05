;;
;; Copyright © 2021 Sam Ritchie.
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

(ns sicmutils.quaternion
  (:require [sicmutils.complex :as sc]
            [sicmutils.differential :as d]
            [sicmutils.function :as f]
            [sicmutils.generic :as g]
            [sicmutils.util.logic :as ul]
            [sicmutils.matrix :as m]
            [sicmutils.structure :as ss]
            [sicmutils.util :as u]
            [sicmutils.value :as v])
  #?(:clj
     (:import (clojure.lang AFn Associative Counted IObj IFn Sequential))))

;; TODO: look at `assume!` in scmutils. What if you can show that the thing IS
;; indeed true? Does the logging just happen as the default failure?
;;
;; NOTE AND you might not want to normalize every damned time, if you have some
;; symbolic thing. Do it twice and it's going to get crazy. So why not just
;; assume that it's normalized?

;; TODO:
;; - dot-product and whatever else fits... https://github.com/typelevel/spire/blob/master/core/src/main/scala/spire/math/Quaternion.scala#L302
;; - sqrt https://github.com/typelevel/spire/blob/master/core/src/main/scala/spire/math/Quaternion.scala#L202
;; - signum function! https://github.com/typelevel/spire/blob/master/core/src/main/scala/spire/math/Quaternion.scala#L174
;;
;; TODO slerp, fromAxisAngle (confirm), fromEuler (confirm), fromBetweenVectors https://github.com/infusion/Quaternion.js/
;;
;; TODO confirm we have ->3x3 matrix and ->4x4 matrix https://github.com/infusion/Quaternion.js/
;;
;; TODO quaternion sinum  https://github.com/typelevel/spire/blob/master/core/src/main/scala/spire/math/Quaternion.scala#L187

;; TODO see remaining interfaces here, and PICK the functions below: https://github.com/weavejester/euclidean/blob/master/src/euclidean/math/quaternion.clj

(declare arity eq evaluate q:zero? one?)

(deftype Quaternion [r i j k m]
  f/IArity
  (arity [this] (arity this))

  d/IPerturbed
  (perturbed? [_]
    (or (d/perturbed? r)
        (d/perturbed? i)
        (d/perturbed? j)
        (d/perturbed? k)))

  (replace-tag [_ old new]
    (Quaternion.
     (d/replace-tag r old new)
     (d/replace-tag i old new)
     (d/replace-tag j old new)
     (d/replace-tag k old new)
     m))

  (extract-tangent [_ tag]
    (Quaternion.
     (d/extract-tangent r tag)
     (d/extract-tangent i tag)
     (d/extract-tangent j tag)
     (d/extract-tangent k tag)
     m))

  v/Value
  (zero? [this] (q:zero? this))
  (one? [this] (one? this))
  (identity? [this] (one? this))

  (zero-like [this]
    (Quaternion. (v/zero-like r) 0 0 0 m))
  (one-like [o]
    (Quaternion. (v/one-like r) 0 0 0 m))
  (identity-like [o]
    (Quaternion. (v/one-like r) 0 0 0 m))

  (exact? [this]
    (and (v/exact? r)
         (v/exact? i)
         (v/exact? j)
         (v/exact? k)))
  (freeze [_]
    (list 'quaternion r i j k))
  (kind [_] ::quaternion)

  #?@(:clj
      [Object
       (toString [_] (str "#sicm/quaternion [" r " " i  " " j " " k "]"))
       (equals [self q] (eq self q))

       IObj
       (meta [_] m)
       (withMeta [_ m] (Quaternion. r i j k m))

       Sequential

       Associative
       (assoc [_ k v]
              (case k
                0 (Quaternion. v i j k m)
                1 (Quaternion. r v j k m)
                2 (Quaternion. r i v k m)
                3 (Quaternion. r i j v m)
                (IndexOutOfBoundsException.)))

       (containsKey [_ k] (boolean (#{0 1 2 3} k)))
       (entryAt [this k]
                (get this k nil))

       ;; TODO: count does NOT get called, it seems!!
       (count [_] 4)
       (seq [_] (list r i j k))
       (valAt [this k]
              (get this k nil))
       (valAt [_ k default]
              (case k
                0 r
                1 i
                2 j
                3 k
                default))
       (empty [this] (Quaternion. 0 0 0 0 m))
       (equiv [this that] (.equals this that))

       IFn
       (invoke [this]
               (evaluate this []))
       (invoke [this a]
               (evaluate this [a]))
       (invoke [this a b]
               (evaluate this [a b]))
       (invoke [this a b c]
               (evaluate this [a b c]))
       (invoke [this a b c d]
               (evaluate this [a b c d]))
       (invoke [this a b c d e]
               (evaluate this [a b c d e]))
       (invoke [this a b c d e f]
               (evaluate this [a b c d e f]))
       (invoke [this a b c d e f g]
               (evaluate this [a b c d e f g]))
       (invoke [this a b c d e f g h]
               (evaluate this [a b c d e f g h]))
       (invoke [this a b c d e f g h i]
               (evaluate this [a b c d e f g h i]))
       (invoke [this a b c d e f g h i j]
               (evaluate this [a b c d e f g h i j]))
       (invoke [this a b c d e f g h i j k]
               (evaluate this [a b c d e f g h i j k]))
       (invoke [this a b c d e f g h i j k l]
               (evaluate this [a b c d e f g h i j k l]))
       (invoke [this a b c d e f g h i j k l m]
               (evaluate this [a b c d e f g h i j k l m]))
       (invoke [this a b c d e f g h i j k l m n]
               (evaluate this [a b c d e f g h i j k l m n]))
       (invoke [this a b c d e f g h i j k l m n o]
               (evaluate this [a b c d e f g h i j k l m n o]))
       (invoke [this a b c d e f g h i j k l m n o p]
               (evaluate this [a b c d e f g h i j k l m n o p]))
       (invoke [this a b c d e f g h i j k l m n o p q]
               (evaluate this [a b c d e f g h i j k l m n o p q]))
       (invoke [this a b c d e f g h i j k l m n o p q r]
               (evaluate this [a b c d e f g h i j k l m n o p q r]))
       (invoke [this a b c d e f g h i j k l m n o p q r s]
               (evaluate this [a b c d e f g h i j k l m n o p q r s]))
       (invoke [this a b c d e f g h i j k l m n o p q r s t]
               (evaluate this [a b c d e f g h i j k l m n o p q r s t]))
       (invoke [this a b c d e f g h i j k l m n o p q r s t rest]
               (evaluate this (into [a b c d e f g h i j k l m n o p q r s t] rest)))
       (applyTo [s xs] (AFn/applyToHelper s xs))]

      :cljs
      [Object
       (toString [_]
                 (str "#sicm/quaternion [" r " " i  " " j " " k "]"))

       IMeta
       (-meta [_] m)

       IWithMeta
       (-with-meta [_ meta] (Quaternion. r i j k meta))

       IPrintWithWriter
       (-pr-writer [x writer _]
                   (write-all writer (.toString x)))

       ;; ICollection
       ;; (-conj [_ item] (. orientation (-conj v item) m))

       IEmptyableCollection
       (-empty [_] (Quaternion. 0 0 0 0 m))

       ISequential

       IEquiv
       (-equiv [this that] (eq this that))

       ISeqable
       (-seq [_] (list r i j k))

       ICounted
       (-count [_] 4)

       ;; IIndexed
       ;; (-nth [_ n] (-nth v n))
       ;; (-nth [_ n not-found] (-nth v n not-found))

       ;; ILookup
       ;; (-lookup [_ k] (-lookup v k))
       ;; (-lookup [_ k not-found] (-lookup v k not-found))

       ;; IAssociative
       ;; (-assoc [_ k entry] (Structure. orientation (-assoc v k entry) m))
       ;; (-contains-key? [_ k] (-contains-key? v k))

       ;; IFind
       ;; (-find [_ n] (-find v n))

       ;; IReduce
       ;; (-reduce [_ f] (-reduce v f))
       ;; (-reduce [_ f start] (-reduce v f start))

       IFn
       (-invoke [this]
                (evaluate this []))
       (-invoke [this a]
                (evaluate this [a]))
       (-invoke [this a b]
                (evaluate this [a b]))
       (-invoke [this a b c]
                (evaluate this [a b c]))
       (-invoke [this a b c d]
                (evaluate this [a b c d]))
       (-invoke [this a b c d e]
                (evaluate this [a b c d e]))
       (-invoke [this a b c d e f]
                (evaluate this [a b c d e f]))
       (-invoke [this a b c d e f g]
                (evaluate this [a b c d e f g]))
       (-invoke [this a b c d e f g h]
                (evaluate this [a b c d e f g h]))
       (-invoke [this a b c d e f g h i]
                (evaluate this [a b c d e f g h i]))
       (-invoke [this a b c d e f g h i j]
                (evaluate this [a b c d e f g h i j]))
       (-invoke [this a b c d e f g h i j k]
                (evaluate this [a b c d e f g h i j k]))
       (-invoke [this a b c d e f g h i j k l]
                (evaluate this [a b c d e f g h i j k l]))
       (-invoke [this a b c d e f g h i j k l m]
                (evaluate this [a b c d e f g h i j k l m]))
       (-invoke [this a b c d e f g h i j k l m n]
                (evaluate this [a b c d e f g h i j k l m n]))
       (-invoke [this a b c d e f g h i j k l m n o]
                (evaluate this [a b c d e f g h i j k l m n o]))
       (-invoke [this a b c d e f g h i j k l m n o p]
                (evaluate this [a b c d e f g h i j k l m n o p]))
       (-invoke [this a b c d e f g h i j k l m n o p q]
                (evaluate this [a b c d e f g h i j k l m n o p q]))
       (-invoke [this a b c d e f g h i j k l m n o p q r]
                (evaluate this [a b c d e f g h i j k l m n o p q r]))
       (-invoke [this a b c d e f g h i j k l m n o p q r s]
                (evaluate this [a b c d e f g h i j k l m n o p q r s]))
       (-invoke [this a b c d e f g h i j k l m n o p q r s t]
                (evaluate this [a b c d e f g h i j k l m n o p q r s t]))
       (-invoke [this a b c d e f g h i j k l m n o p q r s t rest]
                (evaluate this (into [a b c d e f g h i j k l m n o p q r s t] rest)))]))

(do (ns-unmap 'sicmutils.quaternion '->Quaternion)
    (defn ->Quaternion
      "Positional factory function for [[Quaternion]].

  The final argument `m` defaults to nil if not supplied."
      ([r i j k]
       (Quaternion. r i j k nil))
      ([r i j k m]
       (Quaternion. r i j k m))))

#?(:clj
   (defmethod print-method Quaternion
     [^Quaternion q ^java.io.Writer w]
     (.write w (.toString q))))

(defn quaternion? [q]
  (instance? Quaternion q))

(defn get-r
  "Get the r component of a quaternion."
  [^Quaternion q]
  (.-r q))

(defn real-part
  "Get the r component of a quaternion."
  [^Quaternion q]
  (.-r q))

(defn get-i
  "Get the i component of a quaternion."
  [^Quaternion q]
  (.-i q))

(defn get-j
  "Get the j component of a quaternion."
  [^Quaternion q]
  (.-j q))

(defn get-k
  "Get the k component of a quaternion."
  [^Quaternion q]
  (.-k q))

(defn ->complex
  "Returns a complex number created from the real and imaginary
  components (dropping j, k)."
  [q]
  {:pre [(quaternion? q)]}
  (sc/complex (get-r q)
              (get-i q)))

(defn ->vector
  "Returns a 4-vector form of the quaternion. `seq` works too, but this returns a
  vector."
  [q]
  {:pre [(quaternion? q)]}
  [(get-r q) (get-i q) (get-j q) (get-k q)])

(defn three-vector
  "Returns the `imaginary` piece of the quaternion as a vector."
  [q]
  {:pre [(quaternion? q)]}
  [(get-i q) (get-j q) (get-k q)])

;; ## Constructors
;;
;; TODO check for duplicates!

(def ZERO (->Quaternion 0 0 0 0))

(def ^{:doc "The identity quaternion."}
  ONE (->Quaternion 1 0 0 0))

(def I (->Quaternion 0 1 0 0))
(def J (->Quaternion 0 0 1 0))
(def K (->Quaternion 0 0 0 1))

(defn make
  "Same as `make`, and `real&3vector->quaternion`... plus one more.

  Also handles real and complex args."
  ([x]
   (cond (quaternion? x) x
         (sequential? x) (apply ->Quaternion x)
         (sc/complex? x) (->Quaternion
                          (sc/real x) (sc/imaginary x) 0 0)
         :else (->Quaternion x 0 0 0)))
  ([r [i j k]]
   (->Quaternion r i j k))
  ([r i j k]
   (->Quaternion r i j k)))

;; ## Quaternions and 3D rotations

(defn from-angle-normal-axis
  "Create a quaternion from an angle in radians and a normalized axis vector.

  Call this if you've ALREADY normalized the vector!"
  [angle [x y z]]
  (let [half-angle (g/div angle 2)
        half-sine (g/sin half-angle)]
    (->Quaternion (g/cos half-angle)
                  (g/* half-sine x)
                  (g/* half-sine y)
                  (g/* half-sine z))))

(defn from-angle-axis
  "Create a quaternion from an angle in radians and an arbitrary axis vector."
  [angle axis]
  (let [vv     (g/abs axis)
        normal (g/div axis vv)]
    (from-angle-normal-axis angle normal)))

;; NOTE this is the scmutils way of doing it. assumption is good... otherwise
;; the same. It ASSUMES instead of normalizing.

(defn angle-axis->
  "NOTE from gjs: Given a axis (a unit 3-vector) and an angle...

  TODO change name?"
  [theta axis]
  (let [v (g/simplify
           (ss/vector-dot-product axis axis))]
    (ul/assume! (list '= v 1) 'angle-axis->))
  (make (g/cos (g// theta 2))
        (g/* (g/sin (g// theta 2))
             axis)))

(defn ->angle-axis
  "TODO complete! this is old, from GJS.

  Problem: this is singular if the vector part is zero."
  ([q] (->angle-axis q vector))
  ([q continue]
   {:pre [(quaternion? q)]}
   (let [v (three-vector q)
         theta (g/mul 2 (g/atan
                         (g/abs v)
                         (real-part q)))
         axis (g/div v (g/abs v))]
     (continue theta axis))))

(defn pitch
  "Create a quaternion representing a pitch rotation by an angle in radians."
  [angle]
  (from-angle-normal-axis angle [1 0 0]))

(defn yaw
  "Create a quaternion representing a yaw rotation by an angle in radians."
  [angle]
  (from-angle-normal-axis angle [0 1 0]))

(defn roll
  "Create a quaternion representing a roll rotation by an angle in radians."
  [angle]
  (from-angle-normal-axis angle [0 0 1]))

(comment
  (defn axes
    "Return the three axes of the quaternion."
    [^Quaternion q]
    (let [n  (norm q),  s  (if (> n 0) (/ 2.0 n) 0.0)
          x  (.getX q), y  (.getY q), z  (.getZ q), w  (.getW q)
          xs (* x s),   ys (* y s),   zs (* z s),   ws (* w s)
          xx (* x xs),  xy (* x ys),  xz (* x zs),  xw (* x ws)
          yy (* y ys),  yz (* y zs),  yw (* y ws)
          zz (* z zs),  zw (* z ws)]
      [(Vector3D. (- 1.0 (+ yy zz)) (+ xy zw) (- xz yw))
       (Vector3D. (- xy zw) (- 1.0 (+ xx zz)) (+ yz xw))
       (Vector3D. (+ xz yw) (- yz xw) (- 1.0 (+ xx yy)))])))

(comment
  (defn from-axes
    "Create a quaternion from three axis vectors."
    [^Vector3D x-axis ^Vector3D y-axis ^Vector3D z-axis]
    (let [m00 (.getX x-axis), m01 (.getX y-axis), m02 (.getX z-axis)
          m10 (.getY x-axis), m11 (.getY y-axis), m12 (.getY z-axis)
          m20 (.getZ x-axis), m21 (.getZ y-axis), m22 (.getZ z-axis)
          trace (+ m00 m11 m22)]
      (cond
        (>= trace 0)
        (let [s (Math/sqrt (inc trace))
              r (/ 0.5 s)]
          (Quaternion. (* r (- m21 m12))
                       (* r (- m02 m20))
                       (* r (- m10 m01))
                       (* 0.5 s)))
        (and (> m00 m11) (> m00 m22))
        (let [s (Math/sqrt (- (inc m00) m11 m22))
              r (/ 0.5 s)]
          (Quaternion. (* 0.5 s)
                       (* r (+ m10 m01))
                       (* r (+ m02 m20))
                       (* r (- m21 m12))))
        (> m11 m22)
        (let [s (Math/sqrt (- (inc m11) m00 m22))
              r (/ 0.5 s)]
          (Quaternion. (* r (+ m10 m01))
                       (* 0.5 s)
                       (* r (+ m21 m12))
                       (* r (- m02 m20))))
        :else
        (let [s (Math/sqrt (- (inc m22) m00 m11))
              r (/ 0.5 s)]
          (Quaternion. (* r (+ m02 m20))
                       (* r (+ m21 m12))
                       (* 0.5 s)
                       (* r (- m10 m01))))))))

(comment
  (defn look-at
    "Create a quaternion that is directed at a point specified by a vector."
    [direction up]
    (let [z-axis (v/normalize direction)
          x-axis (v/normalize (v/cross up direction))
          y-axis (v/normalize (v/cross direction x-axis))]
      (from-axes x-axis y-axis z-axis))))

;; ## Predicates, Accessors

(defn real?
  "Returns true if `q` has zero entries for all non-real fields, false otherwise."
  [q]
  (and (v/zero? (get-i q))
       (v/zero? (get-j q))
       (v/zero? (get-k q))))

(defn pure?
  "Returns true if `q` has a zero real entry, false otherwise."
  [q]
  (v/zero? (get-r q)))

;; TODO vector dot product, just do it directly.

(defn unit?
  "Returns true if `q` is a unit quaternion (ie, a 'versor'), false otherwise."
  [q]
  (let [v (->vector q)]
    (v/one?
     (ss/vector-dot-product v v))))

(defn q:zero?
  "TODO remove `q` prefix."
  [q]
  (and (real? q) (v/zero? (get-r q))))

(defn one? [q]
  (and (real? q) (v/one? (get-r q))))

(defn eq
  "Equality that handles quaternion, complex, vector-like entries and bails out to
  comparing real parts."
  [q1 q2]
  (or (identical? q1 q2)
      (let [r (get-r q1)
            i (get-i q1)
            j (get-j q1)
            k (get-k q1)]
        (cond
          (quaternion? q2)
          (let [q2 ^Quaternion q2]
            (and (v/= r (.-r q2))
                 (v/= i (.-i q2))
                 (v/= j (.-j q2))
                 (v/= k (.-k q2))))

          (sc/complex? q2)
          (and (v/= r (sc/real q2))
               (v/= i (sc/imaginary q2))
               (v/zero? j)
               (v/zero? k))

          (counted? q2)
          (and (= (count q2) 4)
               (= r (q2 0))
               (= i (q2 1))
               (= j (q2 2))
               (= k (q2 3)))

          :else
          (and (real? q1)
               (v/= r q2))))))

;; ## Quaternions with Function Coefficients

(defn arity
  "Returns an arity compatible with all function coefficient entries."
  [q]
  (f/seq-arity
   (->vector q)))

(defn evaluate
  "Takes a quaternion `q` with function coefficients and a sequence `args` of
  arguments, and returns a new [[Quaternion]] generated by applying each
  coefficient to `args`."
  [q args]
  (->Quaternion
   (apply (get-r q) args)
   (apply (get-i q) args)
   (apply (get-j q) args)
   (apply (get-k q) args)))

;; ## Algebra

(defn add [q1 q2]
  (->Quaternion
   (g/add (get-r q1) (get-r q2))
   (g/add (get-i q1) (get-i q2))
   (g/add (get-j q1) (get-j q2))
   (g/add (get-k q1) (get-k q2))))

(defn negate [q]
  (->Quaternion
   (g/negate (get-r q))
   (g/negate (get-i q))
   (g/negate (get-j q))
   (g/negate (get-k q))))

(defn sub [q1 q2]
  (->Quaternion
   (g/sub (get-r q1) (get-r q2))
   (g/sub (get-i q1) (get-i q2))
   (g/sub (get-j q1) (get-j q2))
   (g/sub (get-k q1) (get-k q2))))

(defn mul
  ([q] q)
  ([q1 q2]
   (let [r1 (get-r q1) i1 (get-i q1) j1 (get-j q1) k1 (get-k q1)
         r2 (get-r q2) i2 (get-i q2) j2 (get-j q2) k2 (get-k q2)]
     (->Quaternion
      (g/- (g/* r1 r2) (g/+ (g/* i1 i2) (g/* j1 j2) (g/* k1 k2)))
      (g/+ (g/* r1 i2) (g/* i1 r2) (g/* j1 k2) (g/* -1 k1 j2))
      (g/+ (g/* r1 j2) (g/* -1 i1 k2) (g/* j1 r2) (g/* k1 i2))
      (g/+ (g/* r1 k2) (g/* i1 j2) (g/* -1 j1 i2) (g/* k1 r2)))))
  ([q1 q2 & more]
   (reduce mul (mul q1 q2) more)))

(defn scale-l
  "Returns the result of scaling each coefficient of `q` by `s` with `s` on the
  left."
  [s q]
  (->Quaternion
   (g/* s (get-r q))
   (g/* s (get-i q))
   (g/* s (get-j q))
   (g/* s (get-k q))))

(defn scale
  "Returns the result of scaling each coefficient of `q` by `s` with `s` on the
  right."
  [q s]
  (->Quaternion
   (g/* (get-r q) s)
   (g/* (get-i q) s)
   (g/* (get-j q) s)
   (g/* (get-k q) s)))

(defn conjugate
  "Returns the conjugate of [[Quaternion]] `q`."
  [q]
  (->Quaternion
   (get-r q)
   (g/negate (get-i q))
   (g/negate (get-j q))
   (g/negate (get-k q))))

(defn q-div-scalar [^Quaternion q s]
  (->Quaternion
   (g// (get-r q) s)
   (g// (get-i q) s)
   (g// (get-j q) s)
   (g// (get-k q) s)))

(defn invert [q]
  (q-div-scalar
   (conjugate q)
   (g/+ (g/square (get-r q))
        (g/square (get-i q))
        (g/square (get-j q))
        (g/square (get-k q)))))

(defn div [q1 q2]
  (mul q1 (invert q2)))

(defn magnitude
  "Returns the norm of the quaternion."
  [q]
  (g/sqrt
   (g/+ (g/square (get-r q))
        (g/square (get-i q))
        (g/square (get-j q))
        (g/square (get-k q)))))

(defn normalize
  "Returns a unit quaternion generated from `q`... TODO note what is going on
  here."
  [q]
  (q-div-scalar q (magnitude q)))

(defn exp
  "TODO can we do an axis-angle deal on the right side?"
  [q]
  (let [a (real-part q)
        v (three-vector q)
        vv (g/abs v)
        normal (g/div v vv)]
    (g/mul (g/exp a)
           (make (g/cos vv)
                 (g/* (g/sin vv) normal)))))

(defn log
  "TODO rewrite this, this code is no good..."
  [q]
  (let [a  (real-part q)
        v  (three-vector q)
        qq (g/abs (->vector q))
        vv (g/abs v)]
    (make (g/log qq)
          (g/mul (g/acos (g/div a qq))
                 (g/div v vv)))))

;; ## Calculus

(defn partial-derivative [q selectors]
  (->Quaternion
   (g/partial-derivative (get-r q) selectors)
   (g/partial-derivative (get-i q) selectors)
   (g/partial-derivative (get-j q) selectors)
   (g/partial-derivative (get-k q) selectors)))

;; ## Quaternions as 4x4 matrices

(def ONE-matrix
  (m/by-rows
   [1 0 0 0]
   [0 1 0 0]
   [0 0 1 0]
   [0 0 0 1]))

(def I-matrix
  (m/by-rows
   [0 1 0 0]
   [-1 0 0 0]
   [0 0 0 -1]
   [0 0 1 0]))

(def J-matrix
  (m/by-rows
   [0 0 1 0]
   [0 0 0 1]
   [-1 0 0 0]
   [0 -1 0 0]))

(def K-matrix
  (m/by-rows
   [0 0 0 1]
   [0 0 -1 0]
   [0 1 0 0]
   [-1 0 0 0]))

(def ONE-tensor (m/->structure ONE-matrix))
(def I-tensor (m/->structure I-matrix))
(def J-tensor (m/->structure J-matrix))
(def K-tensor (m/->structure K-matrix))

(defn ->4x4
  "Returns a 4x4 matrix that represents the supplied [[Quaternion]] `q`."
  [q]
  (g/+ (g/* (get-r q) ONE-matrix)
       (g/* (get-i q) I-matrix)
       (g/* (get-j q) J-matrix)
       (g/* (get-k q) K-matrix)))

(defn q:4x4->
  "TODO what is the deal with this first row thing??"
  [four-matrix]
  (make
   (nth four-matrix 0)))

;; To rotate a 3-vector by the angle prescribed by a unit quaternion.

(comment
  ;; TODO see if it's more efficient to do this, AND if so move the next one to
  ;; the tests.

  (defn rotate
    "Rotate a vector with a quaternion."
    [q [vx vy vz]]
    {:pre [(quaternion? q)
           (vector? v)
           (= 3 (count v))]}
    (let [qx (.getX q), qy (.getY q), qz (.getZ q), qw (.getW q)]
      [(+ (* qw qw vx)     (* 2 qy qw vz) (* -2 qz qw vy)  (* qx qx vx)
          (* 2 qy qx vy)   (* 2 qz qx vz) (- (* qz qz vx)) (- (* qy qy vx)))
       (+ (* 2 qx qy vx)   (* qy qy vy)   (* 2 qz qy vz)   (* 2 qw qz vx)
          (- (* qz qz vy)) (* qw qw vy)   (* -2 qx qw vz)  (- (* qx qx vy)))
       (+ (* 2 qx qz vx)   (* 2 qy qz vy) (* qz qz vz)     (* -2 qw qy vx)
          (- (* qy qy vz)) (* 2 qw qx vy) (- (* qx qx vz)) (* qw qw vz))]))
  )

(defn rotate [q]
  {:pre [(quaternion? q)]}
  ;;(assert (q:unit? q)) This assertion is really:

  ;; TODO log this `assume-unit!` as a new function, have it throw on false.
  (let [vv (->vector q)
        v  (g/simplify (g/dot-product vv vv))]
    (ul/assume! (list '= v 1) 'rotate))
  (let [q* (conjugate q)]
    (fn the-rotation [three-v]
      (three-vector
       (mul q (make 0 three-v) q*)))))

;; ## Relation to 3x3 Rotation Matrices
;;
;; Expanded Matt Mason method.

;; TODO get the simplify stuff going, maybe in a separate PR... AND remove this
;; `careful-simplify` stuff.

(def careful-simplify g/simplify)

(defn rotation-matrix->
  "TODO change >= etc to using compare... OR just go ahead and add those to v/
  finally!"
  [M]
  ;; (assert (orthogonal-matrix? M))
  ;; returns a unit quaternion
  (let [r11 (get-in M [0 0]) r12 (get-in M [0 1]) r13 (get-in M [0 2])
        r21 (get-in M [1 0]) r22 (get-in M [1 1]) r23 (get-in M [1 2])
        r31 (get-in M [2 0]) r32 (get-in M [2 1]) r33 (get-in M [2 2])
        quarter (g// 1 4)

        q0-2 (g/* quarter (g/+ 1 r11 r22 r33))
        q1-2 (g/* quarter (g/+ 1 r11 (g/- r22) (g/- r33)))
        q2-2 (g/* quarter (g/+ 1 (g/- r11) r22 (g/- r33)))
        q3-2 (g/* quarter (g/+ 1 (g/- r11) (g/- r22) r33))

        q0q1 (g/* quarter (g/- r32 r23))
        q0q2 (g/* quarter (g/- r13 r31))
        q0q3 (g/* quarter (g/- r21 r12))
        q1q2 (g/* quarter (g/+ r12 r21))
        q1q3 (g/* quarter (g/+ r13 r31))
        q2q3 (g/* quarter (g/+ r23 r32))

        q0-2s (careful-simplify q0-2)
        q1-2s (careful-simplify q1-2)
        q2-2s (careful-simplify q2-2)
        q3-2s (careful-simplify q3-2)]
    (cond (and (v/number? q0-2s) (v/number? q1-2s)
               (v/number? q2-2s) (v/number? q3-2s))
          (cond (>= q0-2s (max q1-2s q2-2s q3-2s))
                (let [q0 (g/sqrt q0-2s)
                      q1 (g// q0q1 q0)
                      q2 (g// q0q2 q0)
                      q3 (g// q0q3 q0)]
                  (make q0 q1 q2 q3))

                (>= q1-2s (max q0-2s q2-2s q3-2s))
                (let [q1 (g/sqrt q1-2s)
                      q0 (g// q0q1 q1)
                      q2 (g// q1q2 q1)
                      q3 (g// q1q3 q1)]
                  (make q0 q1 q2 q3))

                (>= q2-2s (max q0-2s q1-2s q3-2s))
                (let [q2 (g/sqrt q2-2s)
                      q0 (g// q0q2 q2)
                      q1 (g// q1q2 q2)
                      q3 (g// q2q3 q2)]
                  (make q0 q1 q2 q3))

                :else
                (let [q3 (g/sqrt q3-2s)
                      q0 (g// q0q3 q3)
                      q1 (g// q1q3 q3)
                      q2 (g// q2q3 q3)]
                  (make q0 q1 q2 q3)))

          (not (v/numeric-zero? q0-2s))
          (let [q0 (g/sqrt q0-2)
                q1 (g// q0q1 q0)
                q2 (g// q0q2 q0)
                q3 (g// q0q3 q0)]
            (make q0 q1 q2 q3))

          (not (v/numeric-zero? q1-2s))
          (let [q1 (g/sqrt q1-2)
                q0 0
                q2 (g// q1q2 q1)
                q3 (g// q1q3 q1)]
            (make q0 q1 q2 q3))

          (not (v/numeric-zero? q2-2s))
          (let [q2 (g/sqrt q2-2)
                q0 0
                q1 0
                q3 (g// q2q3 q2)]
            (make q0 q1 q2 q3))

          :else (make 0 0 0 0))))

(defn ->rotation-matrix [q]
  {:pre [(quaternion? q)]}
  ;;(assert (q:unit? q))
  ;; This assertion is really:
  ;; TODO same thing, pull out assumption!!
  (let [vv (->vector q)
        v  (g/simplify (g/dot-product vv vv))]
    (ul/assume! (list '= v 1) 'quaternion->rotation-matrix))
  (let [q0 (get-r q) q1 (get-i q)
        q2 (get-j q) q3 (get-k q)
        m-2 (g/+ (g/square q0) (g/square q1)
                 (g/square q2) (g/square q3))]
    (m/by-rows [(g// (g/+ (g/expt q0 2)
                          (g/expt q1 2)
                          (g/* -1 (g/expt q2 2))
                          (g/* -1 (g/expt q3 2)))
                     m-2)
                (g// (g/* 2 (g/- (g/* q1 q2) (g/* q0 q3)))
                     m-2)
                (g// (g/* 2 (g/+ (g/* q1 q3) (g/* q0 q2)))
                     m-2)]
               [(g// (g/* 2 (g/+ (g/* q1 q2) (g/* q0 q3)))
                     m-2)
                (g// (g/+ (g/expt q0 2)
                          (g/* -1 (g/expt q1 2))
                          (g/expt q2 2)
                          (g/* -1 (g/expt q3 2)))
                     m-2)
                (g// (g/* 2 (g/- (g/* q2 q3) (g/* q0 q1)))
                     m-2)]
               [(g// (g/* 2 (g/- (g/* q1 q3) (g/* q0 q2)))
                     m-2)
                (g// (g/* 2 (g/+ (g/* q2 q3) (g/* q0 q1)))
                     m-2)
                (g// (g/+ (g/expt q0 2)
                          (g/* -1 (g/expt q1 2))
                          (g/* -1 (g/expt q2 2))
                          (g/expt q3 2))
                     m-2)])))

;; Generic Method Installation

(defmethod v/= [::quaternion ::quaternion] [a b] (eq a b))

;; TODO test... maybe we just need vectors, not sequences?
(defmethod v/= [v/seqtype ::quaternion] [a b] (eq a b))
(defmethod v/= [::quaternion v/seqtype] [a b] (eq a b))

;; TODO: https://github.com/typelevel/spire/blob/master/core/src/main/scala/spire/math/Quaternion.scala#L139
(defmethod v/= [::sc/complex ::quaternion] [a b])
(defmethod v/= [::quaternion ::sc/complex] [a b])

;; TODO... this could be scalar for sure. https://github.com/typelevel/spire/blob/master/core/src/main/scala/spire/math/Quaternion.scala#L141
(defmethod v/= [::v/real ::quaternion] [a b])
(defmethod v/= [::quaternion ::v/real] [a b])

(defmethod g/simplify [::quaternion] [^Quaternion q]
  (->Quaternion
   (g/simplify (get-r q))
   (g/simplify (get-i q))
   (g/simplify (get-j q))
   (g/simplify (get-k q))
   (meta q)))

;; TODO: add, sub, mul, div with complex and scalar separately? https://github.com/typelevel/spire/blob/master/core/src/main/scala/spire/math/Quaternion.scala#L234

(defmethod g/add [::quaternion ::quaternion] [a b] (add a b))

(defmethod g/negate [::quaternion] [q] (negate q))
(defmethod g/sub [::quaternion ::quaternion] [a b] (sub a b))

(defmethod g/mul [::quaternion ::quaternion] [a b] (mul a b))
(defmethod g/mul [::v/scalar ::quaternion] [s q] (scale-l s q))
(defmethod g/mul [::quaternion ::v/scalar] [q s] (scale q s))

(defmethod g/invert [::quaternion] [q] (invert q))
(defmethod g/div [::quaternion ::v/scalar] [q s] (q-div-scalar q s))
(defmethod g/div [::quaternion ::quaternion] [a b] (div a b))

(defmethod g/magnitude [::quaternion] [q] (magnitude q))
(defmethod g/conjugate [::quaternion] [q] (conjugate q))
(defmethod g/real-part [::quaternion] [q] (real-part q))
(defmethod g/exp [::quaternion] [q] (exp q))
(defmethod g/log [::quaternion] [q] (log q))

(defmethod g/partial-derivative [::quaternion v/seqtype] [q selectors]
  (partial-derivative q selectors))

(defmethod g/solve-linear-right [::quaternion ::scalar] [q s] (q-div-scalar q s))
(defmethod g/solve-linear-right [::quaternion ::quaternion] [a b] (div a b))

(defmethod g/solve-linear [::v/scalar ::quaternion] [s q] (q-div-scalar q s))
(defmethod g/solve-linear [::quaternion ::quaternion] [a b] (div b a))
