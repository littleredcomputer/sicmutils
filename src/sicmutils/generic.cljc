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

(ns sicmutils.generic
  "The home of most of the SICMUtils extensible generic operations. The bulk of
  the others live in [[sicmutils.value]].

  See [the `Generics`
  cljdocs](https://cljdoc.org/d/sicmutils/sicmutils/CURRENT/doc/basics/generics)
  for a detailed discussion of how to use and extend the generic operations
  defined in [[sicmutils.generic]] and [[sicmutils.value]]."
  (:refer-clojure :exclude [/ + - * divide])
  (:require [sicmutils.value :as v]
            [sicmutils.util :as u]
            [sicmutils.util.def :refer [defgeneric]
             #?@(:cljs [:include-macros true])]))

;; ## Generic Numerics
;;
;; The first section introduces generic versions of
;; Clojure's [[+]], [[-]], [[*]] and [[/]] operations. Any type that can
;; implement all four of these operations forms a
;; mathematical [Field](https://en.wikipedia.org/wiki/Field_(mathematics)).
;;
;; There are, of course, other technical names for types that can only implement
;; a subset of these operations, and more specializations of those names
;; depending on whether or not the implementation of these binary operations is
;; commutative or
;; associative. (See [Semigroup](https://en.wikipedia.org/wiki/Semigroup)
;; and [Monoid](https://en.wikipedia.org/wiki/Monoid) first, and start exploring
;; the realm of abstract algebra from there.)
;;
;; This library takes a permissive stance on extensibility. Types extend the
;; arithmetic operators by extending their unary or binary cases:
;;
;; - [[add]] for [[+]]
;; - [[sub]] and [[negate]] for [[-]]
;; - [[mul]] for [[*]]
;; - [[invert]] and [[div]] for [[/]]
;;
;; And the higher arity version reduces its list of arguments using this binary
;; operation. This makes it possible and easy to make the arithmetic operators
;; combine different types! It's up to you to do this in a mathematically
;; responsible way.
;;
;; Dispatch occurs via [[value/argument-kind]]. Documentation on how to extend
;; each generic operation to some new type is sparse. Have a look
;; at [[sicmutils.complex]] for an example of how to do this.

(defgeneric ^:no-doc add 2
  "Returns the sum of arguments `a` and `b`.

  See [[+]] for a variadic version of [[add]]."
  {:name '+
   :dfdx (fn [_ _] 1)
   :dfdy (fn [_ _] 1)})

(defn +
  "Generic implementation of `+`. Returns the sum of all supplied arguments. `(+)`
  returns 0, the additive identity.

  When applied between numbers, acts like `clojure.core/+`. Dispatch is open,
  however, making it possible to 'add' types wherever the behavior is
  mathematically sound.

  For example:

  ```clojure
  (+ [1 2 3] [2 3 4])
  ;;=> (up 3 5 7)
  ```"
  ([] 0)
  ([x] x)
  ([x y]
   (cond (v/zero? x) y
         (v/zero? y) x
         :else (add x y)))
  ([x y & more]
   (reduce + (+ x y) more)))

(defgeneric negate 1
  "Returns the negation of `a`.

  Equivalent to `(- (v/zero-like a) a)`."
  {:name '-
   :dfdx (fn [_] -1)})

(defgeneric ^:no-doc sub 2
  "Returns the difference of `a` and `b`.

  Equivalent to `(+ a (negate b))`.

  See [[-]] for a variadic version of [[sub]]."
  {:name '-
   :dfdx (fn [_ _] 1)
   :dfdy (fn [_ _] -1)})

(defmethod sub :default [a b]
  (add a (negate b)))

(defn -
  "Generic implementation of `-`.

  If one argument is supplied, returns the negation of `a`. Else returns the
  difference of the first argument `a` and the sum of all remaining
  arguments. `(-)` returns 0.

  When applied between numbers, acts like `clojure.core/-`. Dispatch is open,
  however, making it possible to 'subtract' types wherever the behavior is
  mathematically sound.

  For example:

  ```clojure
  (- [1 2 3] [2 3 4])
  ;;=> (up -1 -1 -1)

  (- [1 10])
  ;;=> (up -1 -10)
  ```"
  ([] 0)
  ([x] (negate x))
  ([x y]
   (cond (v/zero? y) x
         (v/zero? x) (negate y)
         :else (sub x y)))
  ([x y & more]
   (- x (apply + y more))))

(defgeneric ^:no-doc mul 2
  "Returns the product of `a` and `b`.

  See [[*]] for a variadic version of [[mul]]."
  {:name '*
   :dfdx (fn [_ y] y)
   :dfdy (fn [x _] x)})

;;; In the binary arity of [[*]] we test for exact (numerical) zero because it
;;; is possible to produce a wrong-type zero here, as follows:
;;;
;;;               |0|             |0|
;;;       |a b c| |0|   |0|       |0|
;;;       |d e f| |0| = |0|, not  |0|
;;;
;;; We are less worried about the v/zero? below,
;;; because any invertible matrix is square.

(defn *
  "Generic implementation of `*`. Returns the product of all supplied
  arguments. `(*)` returns 1, the multiplicative identity.

  When applied between numbers, acts like `clojure.core/*`. Dispatch is open,
  however, making it possible to 'multiply' types wherever the behavior is
  mathematically sound.

  For example:

  ```clojure
  (* 2 #sicm/complex \"3 + 1i\")
  ;;=> #sicm/complex \"6 + 2i\"
  ```"
  ([] 1)
  ([x] x)
  ([x y]
   (cond (and (v/numerical? x) (v/zero? x)) (v/zero-like y)
         (and (v/numerical? y) (v/zero? y)) (v/zero-like x)
         (v/one? x) y
         (v/one? y) x
         :else (mul x y)))
  ([x y & more]
   (reduce * (* x y) more)))

(declare div)

(defgeneric invert 1
  "Returns the multiplicative inverse of `a`.

  Equivalent to `(/ 1 a)`."
  {:name '/
   :dfdx (fn [x] (div -1 (mul x x)))})

(def ^{:dynamic true
       :no-doc true}
  *in-default-invert*
  false)

(defmethod invert :default [a]
  (binding [*in-default-invert* true]
    (div 1 a)))

(defgeneric div 2
  "Returns the result of dividing `a` and `b`.

  Equivalent to `(* a (negate b))`.

  See [[/]] for a variadic version of [[div]]."
  {:name '/
   :dfdx (fn [_ y] (div 1 y))
   :dfdy (fn [x y] (div (negate x)
                       (mul y y)))})

(defmethod div :default [a b]
  (if *in-default-invert*
    (throw
     (ex-info "No implementation of [[invert]] or [[div]]."
              {:method 'div :args [a b]}))
    (mul a (invert b))))

(defn /
  "Generic implementation of `/`.

  If one argument is supplied, returns the multiplicative inverse of `a`. Else
  returns the result of dividing first argument `a` by the product of all
  remaining arguments. `(/)` returns 1, the multiplicative identity.

  When applied between numbers, acts like `clojure.core//`. Dispatch is open,
  however, making it possible to 'divide' types wherever the behavior is
  mathematically sound.

  For example:

  ```clojure
  (/ [2 4 6] 2)
  ;;=> (up 1 2 3)
  ```"
  ([] 1)
  ([x] (invert x))
  ([x y]
   (if (v/one? y)
     x
     (div x y)))
  ([x y & more]
   (/ x (apply * y more))))

(def ^{:doc "Alias for [[/]]."}
  divide
  /)

(defgeneric exact-divide 2
  "Similar to the binary case of [[/]], but throws if `(v/exact? <result>)`
  returns false.")

;; ### Exponentiation, Log, Roots
;;
;; This next batch of generics exponentation and its inverse.

(declare negative? log)

(defgeneric expt 2
  {:dfdx (fn [x y]
           (mul y (expt x (sub y 1))))
   :dfdy (fn [x y]
           (if (and (v/number? x) (v/zero? y))
             (if (v/number? y)
               (if (not (negative? y))
                 0
                 (u/illegal "Derivative undefined: expt"))
               0)
             (mul (log x) (expt x y))))})

(defmethod expt :default [s e]
  {:pre [(v/native-integral? e)]}
  (let [kind (v/kind s)]
    (if-let [mul' (get-method mul [kind kind])]
      (letfn [(expt' [base pow]
                (loop [n pow
                       y (v/one-like base)
                       z base]
                  (let [t (even? n)
                        n (quot n 2)]
                    (cond
                      t (recur n y (mul' z z))
                      (zero? n) (mul' z y)
                      :else (recur n (mul' z y) (mul' z z))))))]
        (cond (pos? e)  (expt' s e)
              (zero? e) (v/one-like e)
              :else (invert (expt' s (negate e)))))
      (u/illegal (str "No g/mul implementation registered for kind " kind)))))

(defgeneric square 1)
(defmethod square :default [x] (expt x 2))

(defgeneric cube 1)
(defmethod cube :default [x] (expt x 3))

(defgeneric exp 1
  "Returns the base-e exponential of `x`. Equivalent to `(expt e x)`, given
  some properly-defined `e` symbol."
  {:dfdx exp})

(defgeneric exp2 1
  "Returns the base-2 exponential of `x`. Equivalent to `(expt 2 x)`.")

(defmethod exp2 :default [x] (expt 2 x))

(defgeneric exp10 1
  "Returns the base-10 exponential of `x`. Equivalent to `(expt 10 x)`.")

(defmethod exp10 :default [x] (expt 10 x))

(defgeneric log 1
  "Returns the natural logarithm of `x`."
  {:dfdx invert})

(defgeneric log2 1
  "Returns the base-2 logarithm of `x`, ie, $log_2(x)$.")

(let [l2 (Math/log 2)]
  (defmethod log2 :default [x] (div (log x) l2)))

(defgeneric log10 1
  "Returns the base-10 logarithm of `x`, ie, $log_10(x)$.")

(let [l10 (Math/log 10)]
  (defmethod log10 :default [x] (div (log x) l10)))

(defgeneric sqrt 1
  {:dfdx (fn [x]
           (invert
            (mul (sqrt x) 2)))})

;; ## More Generics

(defgeneric negative? 1
  "Returns true if the argument `a` is less than `(v/zero-like a)`,
  false otherwise. The default implementation depends on a proper Comparable
  implementation on the type.`")

(defmethod negative? :default [a]
  (< a (v/zero-like a)))

(defgeneric abs 1)

(declare integer-part)

(defgeneric floor 1
  "Returns the largest integer less than or equal to `a`.

  Extensions beyond real numbers may behave differently; see the [Documentation
  site](https://cljdoc.org/d/sicmutils/sicmutils/CURRENT/doc/basics/generics)
  for more detail.")

(defmethod floor :default [a]
  (if (negative? a)
    (sub (integer-part a) 1)
    (integer-part a)))

(defgeneric ceiling 1
  "Returns the result of rounding `a` up to the next largest integer.

  Extensions beyond real numbers may behave differently; see the [Documentation
  site](https://cljdoc.org/d/sicmutils/sicmutils/CURRENT/doc/basics/generics)
  for more detail.")

(defmethod ceiling :default [a]
  (negate (floor (negate a))))

(defgeneric integer-part 1
  "Returns the integer part of `a` by removing any fractional digits.")

(defgeneric fractional-part 1
  "Returns the fractional part of the given value, defined as `x - ⌊x⌋`.

  For positive numbers, this is identical to `(- a (integer-part a))`. For
  negative `a`, because [[floor]] truncates toward negative infinity, you might
  be surprised to find that [[fractional-part]] returns the distance between `a`
  and the next-lowest integer:

```clojure
(= 0.6 (fractional-part -0.4))
```")

(defmethod fractional-part :default [a]
  (sub a (floor a)))

(defgeneric quotient 2)

(defn ^:no-doc modulo-default
  "The default implementation for [[modulo]] depends on the identity:

  x mod y == x - y ⌊x/y⌋

  This is the Knuth definition described
  by [Wikipedia](https://en.wikipedia.org/wiki/Modulo_operation)."
  [a b]
  (sub a (mul b (floor (div a b)))))

(defgeneric modulo 2
  "Returns the result of the
  mathematical [Modulo](https://en.wikipedia.org/wiki/Modulo_operation)
  operation between `a` and `b` (using the Knuth definition listed).

 The contract satisfied by [[modulo]] is:

```clojure
(= a (+ (* b (floor (/ a b)))
        (modulo a b)))
```

 For numbers, this differs from the contract offered by [[remainder]]
 because `(floor (/ a b))` rounds toward negative infinity, while
 the [[quotient]] operation in the contract for [[remainder]] rounds toward 0.

 The result will be either `0` or of the same sign as the divisor `b`.")

(defmethod modulo :default [a b]
  (modulo-default a b))

(defn ^:no-doc remainder-default [n d]
  (let [divnd (div n d)]
    (if (= (negative? n) (negative? d))
      (mul d (sub divnd (floor divnd)))
      (mul d (sub divnd (ceiling divnd))))))

(defgeneric remainder 2
  "Returns the remainder of dividing the dividend `a` by divisor `b`.

 The contract satisfied by [[remainder]] is:

```clojure
(= a (+ (* b (quotient a b))
        (remainder a b)))
```

 For numbers, this differs from the contract offered by [[modulo]]
 because [[quotient]] rounds toward 0, while `(floor (/ a b))` rounds toward
 negative infinity.

 The result will be either `0` or of the same sign as the dividend `a`.")

(defmethod remainder :default [n d]
  (remainder-default n d))

;;; FIXME?: is the multi-argument definition required? [[gcd]], I don't fully
;;; understand what [[add]] is doing. Where do I look?
(defgeneric gcd 2
  "Returns the [greatest common
  divisor](https://en.wikipedia.org/wiki/Greatest_common_divisor) of the two
  inputs `a` and `b`."
  {:name '-gcd}) ;;; FIXME?: not sure if this is right

(defn gcd
  "FIXME: add an appropiate comment"
  [x y]
  (if (zero? y)
    x
    (recur y (mod x y))))

(defn -gcd
  ([] 1)
  ([x] x)
  ([x y] (gcd x y))
  ([x y & rest]
   (reduce gcd (gcd x y) rest)))

(defgeneric lcm 2
  "Returns the [least common
  multiple](https://en.wikipedia.org/wiki/Least_common_multiple) of the two
  inputs `a` and `b`.")

;; ### Trigonometric functions

(declare sin)

(defgeneric cos 1
  {:dfdx (fn [x] (negate (sin x)))})

(defgeneric sin 1 {:dfdx cos})

(defgeneric asin 1
  {:dfdx (fn [x]
           (invert
            (sqrt (sub 1 (square x)))))})

(defgeneric acos 1
  {:dfdx (fn [x]
           (negate
            (invert
             (sqrt (sub 1 (square x))))))})

(defgeneric atan [1 2]
  {:dfdx (fn
           ([x]
            (invert (add 1 (square x))))
           ([y x]
            (div x (add (square x)
                        (square y)))))
   :dfdy (fn [y x]
           (div (negate y)
                (add (square x)
                     (square y))))})

(declare sinh)

(defgeneric cosh 1
  {:dfdx sinh})

(defgeneric sinh 1
  {:dfdx cosh})

;; Trig functions with default implementations provided.
(defgeneric tan 1
  {:dfdx (fn [x]
           (invert
            (square (cos x))))})

(defmethod tan :default [x] (div (sin x) (cos x)))

(defgeneric cot 1)
(defmethod cot :default [x] (div (cos x) (sin x)))

(defgeneric sec 1)
(defmethod sec :default [x] (invert (cos x)))

(defgeneric csc 1)
(defmethod csc :default [x] (invert (sin x)))

(defgeneric tanh 1
  {:dfdx (fn [x]
           (sub 1 (square (tanh x))))})

(defmethod tanh :default [x] (div (sinh x) (cosh x)))

(defgeneric sech 1)
(defmethod sech :default [x] (invert (cosh x)))

(defgeneric csch 1)
(defmethod csch :default [x] (invert (sinh x)))

(defgeneric acosh 1)
(defmethod acosh :default [x]
  (mul 2 (log (add
               (sqrt (div (add x 1) 2))
               (sqrt (div (sub x 1) 2))))))

(defgeneric asinh 1)
(defmethod asinh :default [x]
  (log (add x (sqrt (add 1 (square x))))))

(defgeneric atanh 1)
(defmethod atanh :default [x]
  (div (sub (log (add 1 x))
            (log (sub 1 x)))
       2))

;; Complex Operators

(defgeneric make-rectangular 2)
(defgeneric make-polar 2)
(defgeneric real-part 1)
(defgeneric imag-part 1)
(defgeneric magnitude 1)
(defgeneric angle 1)
(defgeneric conjugate 1)

;; Operations on structures

(defgeneric transpose 1)
(defgeneric trace 1)
(defgeneric determinant 1)
(defgeneric dimension 1)
(defgeneric dot-product 2)
(defgeneric inner-product 2)
(defgeneric outer-product 2)
(defgeneric cross-product 2)

;; Structure Defaults

(defmethod transpose [::v/scalar] [a] a)
(defmethod trace [::v/scalar] [a] a)
(defmethod determinant [::v/scalar] [a] a)
(defmethod dimension [::v/scalar] [a] 1)
(defmethod dot-product [::v/scalar ::v/scalar] [l r] (mul l r))
(defmethod inner-product [::v/scalar ::v/scalar] [l r] (mul (conjugate l) r))

;; More advanced generic operations.

(defgeneric partial-derivative 2)
(defgeneric Lie-derivative 1)

(defgeneric simplify 1)
(defmethod simplify :default [a] a)

(defn factorial
  "Returns the factorial of `n`, ie, the product of 1 to `n` (inclusive)."
  [n]
  (apply * (range 1 (inc n))))

;; This call registers a symbol for any non-multimethod we care about. These
;; will be returned instead of the actual function body when the user
;; calls `(v/freeze fn)`, for example.

(v/add-object-symbols!
 {+ '+
  * '*
  - '-
  / '/
  clojure.core/+ '+
  clojure.core/* '*
  clojure.core/- '-
  clojure.core// '/
  clojure.core/mod 'modulo
  clojure.core/quot 'quotient
  clojure.core/rem 'remainder
  clojure.core/neg? 'negative?
  clojure.core/< '<
  clojure.core/<= '<=
  clojure.core/> '>
  clojure.core/>= '>=
  clojure.core/= '=})
