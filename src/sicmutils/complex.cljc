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

(ns sicmutils.complex
  "This namespace provides a number of functions and constructors for working
  with [[Complex]] numbers in Clojure and Clojurescript, and
  installs [[Complex]] into the SICMUtils generic arithmetic system.

  For other numeric extensions, see [[sicmutils.ratio]]
  and [[sicmutils.numbers]]."
  (:require [sicmutils.generic :as g]
            [sicmutils.util :as u]
            [sicmutils.value :as v]
            #?(:cljs [goog.object :as obj])
            #?(:cljs ["complex.js" :as Complex]))
  #?(:clj
     (:import (org.apache.commons.math3.complex Complex ComplexFormat))))

(def ^{:doc "A [[Complex]] value equal to 0 (south pole on the Riemann Sphere)."}
  ZERO
  #?(:clj Complex/ZERO
     :cljs (obj/get Complex "ZERO")))

(def ^{:doc "A [[Complex]] value equal to 1."}
  ONE #?(:clj Complex/ONE
         :cljs (obj/get Complex "ONE")))

(def  ^{:doc "A [[Complex]] value equal to `i`."}
  I
  #?(:clj Complex/I
     :cljs (obj/get Complex "I")))

(def ^:no-doc complextype Complex)

(derive ::complex ::v/number)

(defn complex
  "Returns a [[Complex]] number with the supplied real part `re` and imaginary
  part `im`. `im` defaults to 0."
  ([re]
   (Complex. (u/double re)))
  ([re im]
   (Complex. (u/double re)
             (u/double im))))

(defn complex?
  "Returns true if `a` is an instance of [[Complex]], false otherwise."
  [a]
  (instance? Complex a))

(defn- real [^Complex a]
  #?(:clj (.getReal a)
     :cljs (obj/get a "re")))

(defn- imaginary [^Complex a]
  #?(:clj (.getImaginary a)
     :cljs (obj/get a "im")))

(defmethod g/make-rectangular [::v/real ::v/real] [re im]
  (if (v/zero? im)
    re
    (complex re im)))

(defmethod g/make-polar [::v/real ::v/real] [radius angle]
  (cond (v/zero? radius) radius
        (v/zero? angle)  radius
        :else
        #?(:cljs (Complex. #js {:abs (js/Number radius)
                                :arg (js/Number angle)})
           :clj (let [angle (u/double angle)]
                  (Complex. (* radius (Math/cos angle))
                            (* radius (Math/sin angle)))))))

(defmethod g/real-part [::complex] [a] (real a))
(defmethod g/imag-part [::complex] [a] (imaginary a))
(defmethod g/magnitude [::complex] [^Complex a] (.abs a))
(defmethod g/angle [::complex] [^Complex a] (#?(:clj .getArgument :cljs .arg) a))
(defmethod g/conjugate [::complex] [^Complex a] (.conjugate a))

(#?@(:clj [let [cf (ComplexFormat.)]]
     :cljs [do])
 (defn parse-complex [x]
   "Parser that converts a string representation of a complex number,
  like `1 + 3i`, into a [[Complex]] number object in clj or cljs."
   (cond (string? x)
         #?(:clj
            (let [v (.parse cf x)]
              `(complex ~(real v)
                        ~(imaginary v)))
            :cljs `(complex ~x))

         (vector? x)
         (let [[re im] x]
           (if (or (nil? im)
                   (v/zero? im))
             re
             `(complex ~re ~im)))

         (number? x) x

         :else (u/illegal
                (str "Complex literals must be either strings or vectors. Received: " x)))))

#?(:cljs
   (extend-type Complex
     IEquiv
     (-equiv [this other]
       (cond (complex? other)
             (.equals this other)

             (v/real? other)
             (and (zero? (imaginary this))
                  (v/= (real this) other))

             :else false))

     IPrintWithWriter
     (-pr-writer [x writer opts]
       (write-all
        writer
        "#sicm/complex "
        (str [(obj/get x "re")
              (obj/get x "im")])))))

#?(:clj
   (defmethod print-method Complex [^Complex v ^java.io.Writer w]
     (.write w (str "#sicm/complex "
                    [(.getReal v)
                     (.getImaginary v)]))))

(extend-type Complex
  v/Numerical
  (numerical? [_] true)

  v/Value
  (zero? [c] #?(:clj (= ZERO c) :cljs (.isZero c)))
  (one? [c] (= ONE c))
  (identity? [c] (= ONE c))
  (zero-like [_] ZERO)
  (one-like [_] ONE)
  (identity-like [_] ONE)
  (freeze [c] (let [re (real c)
                    im (imaginary c)]
                (if (v/zero? im)
                  re
                  (list 'complex re im))))
  (exact? [c] (and (v/exact? (real c))
                   (v/exact? (imaginary c))))
  (kind [_] ::complex))

(defmethod v/= [::complex ::complex] [^Complex a ^Complex b]
  (.equals a b))

(defmethod v/= [::complex ::v/real] [^Complex a n]
  (and (zero? (imaginary a))
       (v/= (real a) n)))

(defmethod v/= [::v/real ::complex] [n ^Complex a]
  (and (zero? (imaginary a))
       (v/= n (real a))))

(defmethod g/add [::complex ::complex] [^Complex a ^Complex b]
  (.add a b))

(defmethod g/add [::complex ::v/real] [^Complex a n]
  (.add a ^double (u/double n)))

(defmethod g/add [::v/real ::complex] [n ^Complex a]
  (.add a ^double (u/double n)))

(defmethod g/expt [::complex ::complex] [^Complex a ^Complex b] (.pow a b))
(defmethod g/expt [::complex ::v/real] [^Complex a n] (.pow a ^double (u/double n)))
(defmethod g/expt [::v/real ::complex] [n ^Complex a] (.pow ^Complex (complex n) a))

(defmethod g/abs [::complex] [^Complex a] (.abs a))
(defmethod g/exp [::complex] [^Complex a] (.exp a))
(defmethod g/log [::complex] [^Complex a] (.log a))
(defmethod g/sqrt [::complex] [^Complex a] (.sqrt a))

(defmethod g/sin [::complex] [^Complex a] (.sin a))
(defmethod g/cos [::complex] [^Complex a] (.cos a))
(defmethod g/tan [::complex] [^Complex a] (.tan a))
(defmethod g/asin [::complex] [^Complex a] (.asin a))
(defmethod g/acos [::complex] [^Complex a] (.acos a))
(defmethod g/atan [::complex] [^Complex a] (.atan a))
(defmethod g/cosh [::complex] [^Complex a] (.cosh a))
(defmethod g/sinh [::complex] [^Complex a] (.sinh a))
(defmethod g/tanh [::complex] [^Complex a] (.tanh a))

(defmethod g/integer-part [::complex] [a]
  (let [re (g/integer-part (real a))
        im (g/integer-part (imaginary a))]
    (if (v/zero? im)
      re
      (complex re im))))

(defmethod g/fractional-part [::complex] [a]
  (let [re (g/fractional-part (real a))
        im (g/fractional-part (imaginary a))]
    (if (v/zero? im)
      re
      (complex re im))))

#?(:cljs
   ;; These are all defined explicitly in Complex.js.
   (do
     (defmethod g/cot [::complex] [^Complex a] (.cot a))
     (defmethod g/sec [::complex] [^Complex a] (.sec a))
     (defmethod g/csc [::complex] [^Complex a] (.csc a))
     (defmethod g/tanh [::complex] [^Complex a] (.tanh a))
     (defmethod g/sech [::complex] [^Complex a] (.sech a))
     (defmethod g/csch [::complex] [^Complex a] (.csch a))
     (defmethod g/acosh [::complex] [^Complex a] (.acosh a))
     (defmethod g/asinh [::complex] [^Complex a] (.asinh a))
     (defmethod g/atanh [::complex] [^Complex a] (.atanh a))))

;;The remaining methods have different names in the Clojure vs JS
;;implementations.
#?(:clj
   (do
     (defmethod g/floor [::complex] [^Complex a]
       (let [re (g/floor (.getReal a))
             im (g/floor (.getImaginary a))]
         (if (v/zero? im)
           re
           (complex re im))))

     (defmethod g/ceiling [::complex] [^Complex a]
       (let [re (g/ceiling (.getReal a))
             im (g/ceiling (.getImaginary a))]
         (if (v/zero? im)
           re
           (complex re im))))

     (defmethod g/sub [::complex ::complex] [^Complex a ^Complex b] (.subtract a b))
     (defmethod g/sub [::complex ::v/real] [^Complex a n] (.subtract a (double n)))
     (defmethod g/sub [::v/real ::complex] [n ^Complex a] (.add (.negate a) (double n)))

     (defmethod g/mul [::complex ::complex] [^Complex a ^Complex b] (.multiply a b))
     (defmethod g/mul [::complex ::v/real] [^Complex a n] (.multiply a (double n)))
     (defmethod g/mul [::v/real ::complex] [n ^Complex a] (.multiply a (double n)))

     (defmethod g/div [::complex ::complex] [^Complex a ^Complex b] (.divide a b))
     (defmethod g/div [::complex ::v/real] [^Complex a n] (.divide a (double n)))
     (defmethod g/div [::v/real ::complex] [n ^Complex a] (.multiply (.reciprocal a) (double n)))

     (defmethod g/negate [::complex] [^Complex a] (.negate a))
     (defmethod g/invert [::complex] [^Complex a] (.reciprocal a))
     (defmethod g/square [::complex] [^Complex a] (.multiply a a))
     (defmethod g/cube [::complex] [^Complex a] (.pow a 3.0)))

   :cljs
   (do
     (defmethod g/floor [::complex] [^Complex a] (.floor a))
     (defmethod g/ceiling [::complex] [^Complex a] (.ceil a))
     (defmethod g/sub [::complex ::complex] [^Complex a ^Complex b] (.sub a b))
     (defmethod g/sub [::complex ::v/real] [^Complex a n] (.sub a (u/double n)))
     (defmethod g/sub [::v/real ::complex] [n ^Complex a] (.add (.neg a) (u/double n)))

     (defmethod g/mul [::complex ::complex] [^Complex a ^Complex b] (.mul a b))
     (defmethod g/mul [::complex ::v/real] [^Complex a n] (.mul a (u/double n)))
     (defmethod g/mul [::v/real ::complex] [n ^Complex a] (.mul a (u/double n)))

     (defmethod g/div [::complex ::complex] [^Complex a ^Complex b] (.div a b))
     (defmethod g/div [::complex ::v/real] [^Complex a n] (.div a (u/double n)))
     (defmethod g/div [::v/real ::complex] [n ^Complex a] (.mul ^Complex (.inverse a) (u/double n)))

     (defmethod g/negate [::complex] [^Complex a] (.neg a))
     (defmethod g/invert [::complex] [^Complex a] (.inverse a))
     (defmethod g/square [::complex] [^Complex a] (.mul a a))
     (defmethod g/cube [::complex] [^Complex a] (.pow a 3.0))))
