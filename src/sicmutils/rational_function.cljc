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

(ns sicmutils.rational-function
  (:require [clojure.set :as set]
            [sicmutils.complex :refer [complex?]]
            [sicmutils.differential :as sd]
            [sicmutils.expression.analyze :as a]
            [sicmutils.expression :as x]
            [sicmutils.function :as f]
            [sicmutils.generic :as g]
            [sicmutils.numsymb :as sym]
            [sicmutils.polynomial :as p]
            [sicmutils.polynomial.gcd :as pg]
            [sicmutils.ratio :as r]
            [sicmutils.structure :as ss]
            [sicmutils.util :as u]
            [sicmutils.util.aggregate :as ua]
            [sicmutils.value :as v])
  #?(:clj
     (:import (clojure.lang AFn IFn IObj Seqable))))

;; ## Rational Functions
;;
;; This namespace contains an implementation of rational functions, or rational
;; fractions; the data structure wraps a numerator `u` and denominator `v` where
;; either or both are instances of [[p/Polynomial]]. (If both become
;; non-polynomial, the functions in this namespace drop the [[RationalFunction]]
;; instance down to whatever type is produced by `(g/divide u v)`).
;;
;; The [[RationalFunction]] type wraps up `u` and `v`, the `arity` of the
;; rational function (which must match the arity of `u` and `v`) and optional
;; metadata `m`.

(declare evaluate eq)

(deftype RationalFunction [arity u v m]
  f/IArity
  (arity [_] [:between 0 arity])

  r/IRational
  (numerator [_] u)
  (denominator [_] v)

  sd/IPerturbed
  (perturbed? [_]
    (or (sd/perturbed? u)
        (sd/perturbed? v)))

  (replace-tag [this old new]
    (RationalFunction. arity
                       (sd/replace-tag u old new)
                       (sd/replace-tag v old new)
                       m))

  (extract-tangent [this tag]
    (RationalFunction. arity
                       (sd/extract-tangent u tag)
                       (sd/extract-tangent v tag)
                       m))

  v/Value
  (zero? [_] (v/zero? u))
  (one? [_] (and (v/one? u) (v/one? v)))
  (identity? [_] (and (v/identity? u) (v/one? v)))

  (zero-like [_] (v/zero-like u))
  (one-like [_] (v/one-like u))
  (identity-like [_]
    (RationalFunction. arity
                       (v/identity-like u)
                       (v/one-like v)
                       m))
  (exact? [_] false)
  (freeze [_] (list '/ (v/freeze u) (v/freeze v)))
  (kind [_] ::rational-function)

  #?@(:clj
      [Object
       (equals [this that] (eq this that))
       (toString [p] (pr-str (list '/ u v)))

       IObj
       (meta [_] m)
       (withMeta [_ meta] (RationalFunction. arity u v meta))

       Seqable
       (seq [_] (list u v))

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
               (evaluate this [a b c d e f g h i j k l m n o p q r s t rest]))
       (applyTo [this xs] (AFn/applyToHelper this xs))]

      :cljs
      [Object
       (toString [p] (str u " : " v))

       IEquiv
       (-equiv [this that] (eq this that))

       IMeta
       (-meta [_] m)

       IWithMeta
       (-with-meta [_ m] (RationalFunction. arity u v m))

       ISeqable
       (-seq [_] (list u v))

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
                (evaluate this [a b c d e f g h i j k l m n o p q r s t rest]))

       IPrintWithWriter
       (-pr-writer
        [x writer _]
        (write-all writer
                   "#object[sicmutils.rational-function.RationalFunction \""
                   (.toString x)
                   "\"]"))]))

(do (ns-unmap 'sicmutils.rational-function '->RationalFunction)
    (defn ->RationalFunction
      "Positional factory function for [[RationalFunction]].

  The final argument `m` defaults to nil if not supplied."
      ([arity u v]
       (RationalFunction. arity u v nil))
      ([arity u v m]
       (RationalFunction. arity u v m))))

(defn rational-function?
  "Returns true if the supplied argument is an instance of [[RationalFunction]],
  false otherwise."
  [r]
  (instance? RationalFunction r))

(defn coeff?
  "Returns true if `x` is explicitly _not_ an instance of [[RationalFunction]]
  or [[polynomial/Polynomial]], false if it is."
  [x]
  (and (not (rational-function? x))
       (p/coeff? x)))

(defn ^:no-doc bare-arity
  "Given a [[RationalFunction]] instance `rf`, returns the `arity` field."
  [rf]
  (.-arity ^RationalFunction rf))

(defn ^:no-doc bare-u
  "Given a [[RationalFunction]] instance `rf`, returns the `u` (numerator) field."
  [rf]
  (.-u ^RationalFunction rf))

(defn ^:no-doc bare-v
  "Given a [[RationalFunction]] instance `rf`, returns the `v` (denominator) field."
  [^RationalFunction rf]
  (.-v rf))

(defn arity
  "Returns the declared arity of the supplied [[RationalFunction]]
  or [[polynomial/Polynomial]], or `0` for arguments of other types."
  [r]
  (if (rational-function? r)
    (bare-arity r)
    (p/arity r)))

(defn- check-same-arity
  "Given two inputs `u` and `v`, checks that their arities are equal and returns
  the value, or throws an exception if not.

  If either `p` or `q` is a coefficient with [[arity]] equal to
  0, [[check-same-arity]] successfully returns the other argument's arity."
  [u v]
  (let [ua (arity u)
        va (arity v)]
    (cond (zero? ua) va
          (zero? va) ua
          (= ua va) ua
          :else (u/illegal (str "Unequal arities: " u ", " v)))))

(defn eq
  "Returns true if the [[RationalFunction]] this is equal to `that`. If `that` is
  a [[RationalFunction]], `this` and `that` are equal if they have equal `u` and
  `v` and equal arity. `u` and `v` entries are compared
  using [[sicmutils.value/=]].

  If `that` is non-[[RationalFunction]], `eq` only returns true if `u` and `v`
  respectively match the [[ratio/numerator]] and [[ratio/denominator]] of
  `that`."
  [^RationalFunction this that]
  (if (instance? RationalFunction that)
    (let [that ^RationalFunction that]
      (and (= (.-arity this) (.-arity that))
           (v/= (.-u this) (.-u that))
           (v/= (.-v this) (.-v that))))

    (and (v/= (.-v this) (r/denominator that))
         (v/= (.-u this) (r/numerator that)))))

;; ## Constructors

(defn- make-reduced
  "Accepts an explicit `arity`, numerator `u` and denominator `v` and returns
  either:

  - `0`, in the case of a [[value/zero?]] numerator
  - `u`, in the case of a [[value/one?]] denominator
  - a [[RationalFunction]] instance if _either_ `u` or `v` is a [[polynomial/Polynomial]]
  - `(g/div u v)` otherwise.

  Call this function when you've already reduced `u` and `v` such that they
  share no common factors and are dropped down to coefficients if possible, and
  want to wrap them in [[RationalFunction]] only when necessary.

  NOTE: The behavior of this mildly-opinionated constructor is similar
  to [[polynomial/terms->polynomial]]"
  [arity u v]
  (cond (v/zero? u) 0
        (v/one? v)  u

        (or (p/polynomial? u)
            (p/polynomial? v))
        (->RationalFunction arity u v)

        :else (g/div u v)))

(defn- coef-sgn
  "Returns `1` if the input is non-numeric or numeric and non-negative, `-1`
  otherwise. In the slightly suspect case of a complex number
  input, [[coef-sgn]] only examines the [[generic/real-part]] of the complex
  number.

  NOTE Negative [[RationalFunction]] instances attempt to keep the negative sign
  in the numerator `u`. The complex number behavior is a kludge, but allows
  canonicalization with complex coefficients."
  [x]
  (cond (v/real? x)
        (if (g/negative? x) -1 1)

        (complex? x)
        (if (g/negative? (g/real-part x)) -1 1)

        :else 1))

(defn- ->reduced
  "First , cancels out any rational numbers in coefficients in the numerator or
  denominator.

  Then, cancel out common factors... finally, reduce to a poly OR constant if
  necessary."
  [u v]
  (when (v/zero? v)
    (u/arithmetic-ex
     "Can't form rational function with zero denominator"))
  (let [a (check-same-arity u v)
        xform (comp (distinct)
                    (map r/denominator))
        coefs  (concat
                (p/coefficients u)
                (p/coefficients v))
        factor (transduce xform (completing g/lcm) 1 coefs)
        factor (if (= 1 (coef-sgn
                         (p/leading-coefficient v)))
                 factor
                 (g/negate factor))
        [u' v'] (if (v/one? factor)
                  [u v]
                  [(g/mul factor u)
                   (g/mul factor v)])
        g (pg/gcd u' v')
        [u'' v''] (if (v/one? g)
                    [u' v']
                    [(p/evenly-divide u' g)
                     (p/evenly-divide v' g)])]
    (make-reduced a u'' v'')))

(defn make
  "Make the fraction of the two polynomials p and q, after dividing out their
  greatest common divisor and normalizing any ratios that appear in numerator or
  denominator."
  [u v]
  (if (and (coeff? u) (coeff? v))
    (g/div u v)
    (->reduced u v)))

;; ## RF Arithmetic
;;
;; Rational arithmetic is from Knuth vol 2 section 4.5.1
;;
;; The notation here is from Knuth (p. 291). In various places we take the gcd
;; of two polynomials and then use quotient to reduce those polynomials.

(defn- binary-combine [l r poly-op uv-op]
  (let [a (check-same-arity l r)
        l-n (r/numerator l)
        l-d (r/denominator l)
        r-n (r/numerator r)
        r-d (r/denominator r)]
    (let [[n d] (if (and (v/one? l-d) (v/one? r-d))
                  [(poly-op l-n r-n) 1]
                  (uv-op l-n l-d r-n r-d))]
      (make-reduced a n d))))

(defn- uv:+
  "Add the [[RationalFunction]] instances `r` and `s`."
  [u u' v v']
  (if (v/= u' v')
    (let [n (p/add u v)
          g (pg/gcd u' n)]
      (if (v/one? g)
        [n u']
        [(p/evenly-divide n g)
         (p/evenly-divide u' g)]))
    (let [d1 (pg/gcd u' v')]
      (if (v/one? d1)
        [(p/add (p/mul u v')
                (p/mul u' v))
         (p/mul u' v')]
        (let [u':d1 (p/evenly-divide u' d1)
              v':d1 (p/evenly-divide v' d1)
              t (p/add (p/mul u v':d1)
                       (p/mul u':d1 v))]
          (if (v/zero? t)
            [0 1]
            (let [d2 (pg/gcd t d1)]
              (if (v/one? d2)
                [t (p/mul u':d1 v')]
                (let [n (p/evenly-divide t d2)
                      d (p/mul u':d1 (p/evenly-divide v' d2))]
                  [n d])))))))))

(defn- uv:* [u u' v v']
  (cond (v/zero? u) [v v']
        (v/zero? v) [u u']
        :else (let [d1 (pg/gcd u v')
                    d2 (pg/gcd u' v)
                    u'' (p/mul (p/evenly-divide u d1)
                               (p/evenly-divide v d2))
                    v'' (p/mul (p/evenly-divide u' d2)
                               (p/evenly-divide v' d1))]
                [u'' v''])))

(defn rf:+ [r s]
  (cond (v/zero? r) s
        (v/zero? s) r
        :else (binary-combine r s p/add uv:+)))

(defn negative? [r]
  (if-not (rational-function? r)
    (p/negative? r)
    (p/negative? (bare-u r))))

(defn negate [r]
  (if-not (rational-function? r)
    (p/negate r)
    (->RationalFunction (bare-arity r)
                        (p/negate (bare-u r))
                        (bare-v r))))

(defn abs [p]
  (if (negative? p)
    (negate p)
    p))

(defn rf:- [r s]
  (cond (v/zero? r) (negate s)
        (v/zero? s) r
        :else
        (binary-combine r s
                        p/sub
                        (fn [u u' v v']
                          (uv:+ u u' (p/negate v) v')))))

(defn rf:* [r s]
  (cond (v/zero? r) r
        (v/zero? s) s
        (v/one? r) s
        (v/one? s) r
        :else (binary-combine r s p/mul uv:*)))

(defn expt [r n]
  {:pre [(v/native-integral? n)]}
  (if-not (rational-function? r)
    (p/expt r n)
    (let [u (bare-u r)
          v (bare-v r)
          [top bottom e] (if (neg? n)
                           [v u (- n)]
                           [u v n])]
      (->RationalFunction (bare-arity r)
                          (p/expt top e)
                          (p/expt bottom e)))))

(defn square [r]
  (if-not (rational-function? r)
    (p/square r)
    (->RationalFunction (bare-arity r)
                        (p/square (bare-u r))
                        (p/square (bare-v r)))))

(defn cube [r]
  (if-not (rational-function? r)
    (p/cube r)
    (->RationalFunction (bare-arity r)
                        (p/cube (bare-u r))
                        (p/cube (bare-v r)))))

(defn invert [r]
  (if-not (rational-function? r)
    (g/invert r)
    (let [u (bare-u r)
          v (bare-v r)]
      (cond (v/zero? u)
            (u/arithmetic-ex
             "Can't form rational function with zero denominator")

            (g/negative? u)
            (->RationalFunction (bare-arity r)
                                (g/negate v)
                                (g/negate u))

            :else (->RationalFunction (bare-arity r) v u)))))

(defn div [r s]
  (rf:* r (invert s)))

(defn- uv:gcd [u u' v v']
  (let [d1 (pg/gcd u v)
        d2 (pg/lcm u' v')]
    (let [result (make d1 d2)]
      [(r/numerator result)
       (r/denominator result)])))

(defn gcd [r s]
  (binary-combine r s pg/gcd uv:gcd))

(defn arg-scale [r factors]
  (if-not (rational-function? r)
    (p/arg-scale r factors)
    (div (p/arg-scale (bare-u r) factors)
         (p/arg-scale (bare-v r) factors))))

(defn arg-shift [r shifts]
  (if-not (rational-function? r)
    (p/arg-shift r shifts)
    (div (p/arg-shift (bare-u r) shifts)
         (p/arg-shift (bare-v r) shifts))))

(defn evaluate [r xs]
  (if-not (rational-function? r)
    (p/evaluate r xs)
    (g/div (p/evaluate (bare-u r) xs)
           (p/evaluate (bare-v r) xs))))

(defn compose
  "only plugs r2 in for the principal indeterminate.

  TODO we COULD do a version that composes with a different one??"
  [r1 r2]
  {:pre [(rational-function? r1)]}
  (if-not (rational-function? r2)
    (g/div (p/evaluate (bare-u r1) r2)
           (p/evaluate (bare-v r1) r2))
    (let [nr1 (bare-u r1)
          nr2 (bare-u r2)
          dr1 (bare-v r1)
          dr2 (bare-v r2)
          dn  (p/degree nr1)
          dd  (p/degree dr1)
          narity (+ (p/arity dr1) 1)
          nnr1 (p/extend 1 (p/reciprocal nr1))
          ndr1 (p/extend 1 (p/reciprocal dr1))
          scales [(second (p/new-variables narity)) 1]
          pn (p/evaluate (p/reciprocal
                          (p/arg-scale nnr1 scales))
                         [nr2 dr2])
          pd (p/evaluate (p/reciprocal
                          (p/arg-scale ndr1 scales))
                         [nr2 dr2])]
      (cond (> dn dd) (g/div pn (p/mul (p/expt dr2 (- dn dd)) pd))
            (< dn dd) (g/div (p/mul (p/expt dr2 (- dd dn)) pn) pd)
            :else (g/div pn pd)))))

(defn partial-derivative [r i]
  (if-not (rational-function? r)
    (p/partial-derivative r i)
    (let [u (bare-u r)
          v (bare-v r)]
      (div (p/sub (p/mul (p/partial-derivative u i) v)
                  (p/mul u (p/partial-derivative v i)))
           (p/square v)))))

(defn partial-derivatives
  "The sequence of partial derivatives of p with respect to each
  indeterminate. Return value has length `(arity p)`."
  [r]
  (if-not (rational-function? r)
    (p/partial-derivatives r)
    (for [i (range (bare-arity r))]
      (partial-derivative r i))))

(def ^:no-doc operator-table
  (assoc p/operator-table
         '/ (ua/group g/div g/mul g/invert 1 v/zero?)
         'invert g/invert))

(def operators-known
  (u/keyset operator-table))

(def ^{:doc "Singleton [[a/ICanonicalize]] instance."}
  analyzer
  (reify a/ICanonicalize
    (expression-> [this expr cont]
      (a/expression-> this expr cont compare))

    (expression-> [this expr cont v-compare]
      ;; Convert an expression into Rational Function canonical form. The
      ;; expression should be an unwrapped expression, i.e., not an instance of
      ;; the Literal type, nor should subexpressions contain type information.
      ;; This kind of simplification proceeds purely symbolically over the known
      ;; Rational Function operations;; other operations outside the arithmetic
      ;; available R(x...) should be factored out by an expression analyzer before
      ;; we get here. The result is a RationalFunction object representing the
      ;; structure of the input over the unknowns."
      (let [expression-vars (sort v-compare
                                  (set/difference (x/variables-in expr)
                                                  operators-known))
            arity (count expression-vars)
            sym->var (zipmap expression-vars (p/new-variables arity))
            expr' (x/evaluate expr sym->var operator-table)]
        (cont expr' expression-vars)))

    (->expression [_ r vars]
      ;; This is the output stage of Rational Function canonical form
      ;; simplification. The input is a RationalFunction, and the output is an
      ;; expression representing the evaluation of that function over the
      ;; indeterminates extracted from the expression at the start of this
      ;; process."
      (if-not (rational-function? r)
        (p/->expression r vars)
        ((sym/symbolic-operator '/)
         (p/->expression (bare-u r) vars)
         (p/->expression (bare-v r) vars))))

    (known-operation? [_ o]
      (contains? operators-known o))))

;; ## Polynomial Extensions
;;
;; Made possible since we can now invert OUT of the ring.

(defmethod g/invert [::p/polynomial] [p]
  (let [a (p/bare-arity p)]
    (if (g/negative? p)
      (->RationalFunction a -1 (g/negate p))
      (->RationalFunction a 1 p))))

(p/defbinary g/div make)
(p/defbinary g/solve-linear-right make)
(p/defbinary g/solve-linear (fn [l r] (div r l)))

;; ## Generic Method Implementations
;;
;; TODO `exact-divide`, `quotient`, `remainder`, `lcm`

(defn ^:no-doc defbinary [generic-op f]
  (let [pairs [[::rational-function ::rational-function]
               [::p/polynomial ::rational-function]
               [::p/coeff ::rational-function]
               [::rational-function ::p/polynomial]
               [::rational-function ::p/coeff]]]
    (doseq [[l r] pairs]
      (defmethod generic-op [l r] [r s]
        (f r s)))))

(defmethod v/= [::rational-function ::rational-function] [l r] (eq l r))
(defmethod v/= [::p/polynomial ::rational-function] [l r] (eq r l))
(defmethod v/= [::p/coeff ::rational-function] [l r] (eq r l))
(defmethod v/= [::rational-function ::p/polynomial] [l r] (eq l r))
(defmethod v/= [::rational-function ::p/coeff] [l r] (eq l r))

(defbinary g/add rf:+)
(defbinary g/sub rf:-)
(defbinary g/mul rf:*)
(defbinary g/div div)
(defbinary g/solve-linear-right div)
(defbinary g/solve-linear (fn [l r] (div r l)))
(defbinary g/gcd gcd)

(defmethod g/negative? [::rational-function] [a] (negative? a))
(defmethod g/abs [::rational-function] [a] (abs a))
(defmethod g/negate [::rational-function] [a] (negate a))
(defmethod g/invert [::rational-function] [a] (invert a))
(defmethod g/square [::rational-function] [a] (square a))
(defmethod g/cube [::rational-function] [a] (square a))

(defmethod g/expt [::rational-function ::v/integral] [b x] (expt b x))

(defmethod g/simplify [::rational-function] [r]
  (->RationalFunction (g/simplify (bare-u r))
                      (g/simplify (bare-v r))
                      (meta r)))

(defmethod g/partial-derivative [::rational-function v/seqtype]
  [p selectors]
  (cond (empty? selectors)
        (ss/down* (partial-derivatives p))

        (= 1 (count selectors))
        (partial-derivative p (first selectors))

        :else
        (u/illegal
         (str "Invalid selector! Only 1 deep supported."))))
