(ns sicmutils.generators
  "test.check generators for the various types in the sicmutils project."
  (:refer-clojure :rename {bigint core-bigint
                           biginteger core-biginteger
                           double core-double
                           long core-long
                           symbol core-symbol}
                  #?@(:cljs [:exclude [bigint double long symbol]]))
  (:require [clojure.test.check.generators :as gen]
            [same :refer [zeroish?]]
            [same.ish :as si]
            [sicmutils.complex :as c]
            [sicmutils.differential :as d]
            [sicmutils.generic :as g]
            [sicmutils.matrix :as m]
            [sicmutils.modint :as mi]
            [sicmutils.numsymb :as sym]
            [sicmutils.polynomial :as poly]
            [sicmutils.polynomial.exponent :as xpt]
            [sicmutils.quaternion :as quat
             #?@(:cljs [:refer [Quaternion]])]
            [sicmutils.ratio :as r]
            [sicmutils.rational-function :as rf]
            [sicmutils.series :as ss]
            [sicmutils.structure :as s]
            [sicmutils.util :as u]
            [sicmutils.util.vector-set :as vs]
            [sicmutils.value :as v])
  #?(:clj
     (:import (org.apache.commons.math3.complex Complex)
              (sicmutils.quaternion Quaternion))))

(def bigint
  "js/BigInt in cljs, clojure.lang.BigInt in clj."
  #?(:cljs
     (gen/fmap u/bigint gen/small-integer)
     :clj
     gen/size-bounded-bigint))

(def biginteger
  #?(:cljs bigint
     :clj (gen/fmap u/biginteger bigint)))

(def native-integral
  "non-floating-point integers on cljs, Long on clj."
  gen/small-integer)

(def long
  "goog.math.Long in cljs,
  java.lang.Long in clj."
  #?(:clj gen/large-integer
     :cljs (gen/fmap u/long gen/large-integer)))

(def integer
  "goog.math.Integer in cljs, java.lang.Integer in clj."
  (gen/fmap u/int gen/small-integer))

(defn reasonable-double
  ([] (reasonable-double {}))
  ([{:keys [min max]
     :or {min -10e5
          max 10e5}}]
   (let [[excluded-lower excluded-upper] [-1e-4 1e-4]]
     (gen/one-of [(gen/double* {:infinite? false
                                :NaN? false
                                :min min
                                :max excluded-lower})
                  (gen/double* {:infinite? false
                                :NaN? false
                                :min excluded-upper
                                :max max})]))))

(defn inexact-double
  ([] (inexact-double {}))
  ([opts]
   (->> (reasonable-double opts)
        (gen/fmap (fn [x]
                    (if (v/exact? x)
                      (+ x 0.5)
                      x))))))

(def small-integral
  (gen/one-of [native-integral long integer]))

(def any-integral
  (gen/one-of [small-integral
               bigint
               #?@(:clj [biginteger])]))

(def ratio
  "Generates a small ratio (or integer) using gen/small-integer. Shrinks
  toward simpler ratios, which may be larger or smaller."
  (gen/fmap
   (fn [[a b]] (r/rationalize a b))
   (gen/tuple gen/small-integer (gen/fmap inc gen/nat))))

(def big-ratio
  (gen/let [n bigint
            d bigint]
    (let [d (if (v/zero? d)
              (u/bigint 1)
              d)]
      (r/rationalize n d))))

(def rational
  (gen/one-of [any-integral ratio]))

(def real-without-ratio
  (gen/one-of [any-integral (reasonable-double)]))

(def real
  (gen/one-of
   [any-integral ratio (reasonable-double)]))

(defn reasonable-real [bound]
  (let [bound    (core-long bound)
        integral (gen/fmap
                  #(g/remainder % bound)
                  any-integral)]
    (gen/one-of [integral (reasonable-double
                           {:min (- bound)
                            :max bound})])))

(def complex
  (gen/let [r (reasonable-double)
            i (reasonable-double)]
    (c/complex r i)))

(def number
  (gen/one-of [real complex]))

;; ## Quaternions

(defn quaternion
  "TODO modify this to kick out more mixed etc!"
  ([] (quaternion (reasonable-double)))
  ([coeff-gen]
   (gen/let [r coeff-gen
             i coeff-gen
             j coeff-gen
             k coeff-gen]
     (quat/make r i j k))))

;; ## Modular Arithmetic

(defn- not-zero [x]
  (if (zero? x) 1 x))

(defn modint
  ([] (modint 30))
  ([modulus-max]
   (gen/let [i gen/small-integer
             m (gen/fmap
                (fn [i]
                  (not-zero
                   (mod i modulus-max)))
                gen/small-integer)]
     (mi/make i m))))

;; ## Symbolic

(def ^{:doc "generates symbols that won't clash with any of the symbols that
  generic operations freeze to, like `*`, `+`, `-`, `/` etc."}
  symbol
  (let [special-syms (set (keys @#'sym/symbolic-operator-table))]
    (gen/such-that (complement special-syms)
                   gen/symbol)))

;; ## Structure Generators

(defn- recursive
  "Similar to `gen/recursive-gen`, but guaranteed to produce containerized
  items (no primitives will pop out)."
  [container elem]
  (container
   (gen/recursive-gen
    container elem)))

(defn structure1*
  "Returns a generator that produces structures of the supplied orientation with
  elements chosen via `elem-gen`. All extra args are passed along to
  `gen/vector`."
  [orientation elem-gen & args]
  {:pre [(s/valid-orientation? orientation)]}
  (gen/fmap (partial s/make orientation)
            (apply gen/vector elem-gen args)))

(defn up1
  "Returns a generator that produces `up` structures of elements chosen via
  `elem-gen`. All extra args are passed along to `gen/vector`."
  [elem-gen & args]
  (apply structure1* ::s/up elem-gen args))

(defn down1
  "Returns a generator that produces `down` structures of elements chosen via
  `elem-gen`. All extra args are passed along to `gen/vector`."
  [elem-gen & args]
  (apply structure1* ::s/down elem-gen args))

(defn structure1
  "Returns a generator that produces `up` or `down` structures of elements chosen
  via `elem-gen`. All extra args are passed along to `gen/vector`."
  [elem-gen & args]
  (gen/one-of [(apply up1 elem-gen args)
               (apply down1 elem-gen args)]))

(defn up
  "Returns a generator that produces nested `up` structures of either further
  `up`s, or primitive elements chosen via `elem-gen`. All extra args are passed
  along to `gen/vector`."
  [elem-gen & args]
  (recursive #(apply up1 % args) elem-gen))

(defn down
  "Returns a generator that produces nested `down` structures of either further
  `down`s, or primitive elements chosen via `elem-gen`. All extra args are passed
  along to `gen/vector`."
  [elem-gen & args]
  (recursive #(apply down1 % args) elem-gen))

(defn structure
  "Returns a generator that produces nested `up` or `down` structures of either
  further containers, or primitive elements chosen via `elem-gen`. All extra
  args are passed along to `gen/vector`."
  [elem-gen & args]
  (recursive #(apply structure1 % args) elem-gen))

(def
  ^{:doc
    "Returns a generator that produces a valid structure orientation"}
  orientation
  (gen/elements [::s/up ::s/down]))

;; ## Matrices

(defn matrix
  "Returns a generator that produces a matrix of the specified dimensions,
  populated with entries from `entry-gen` (if supplied.)

  `entry-gen` defaults to [[ratio]]."
  ([rows cols]
   (matrix rows cols ratio))
  ([rows cols entry-gen]
   (gen/fmap m/by-rows*
             (-> (gen/vector entry-gen rows)
                 (gen/vector cols)))))

(defn square-matrix
  "Returns a generator that produces a square matrix of dimension `n`,
  populated with entries from `entry-gen` (if supplied.)

  `entry-gen` defaults to [[ratio]]."
  ([n] (square-matrix n ratio))
  ([n entry-gen]
   (matrix n n entry-gen)))

;; ## Series

(defn series
  "Generates a [[series/Series]] instance of elements drawn from `entry-gen`.

  `entry-gen` defaults to [[gen/nat]]."
  ([] (series gen/nat))
  ([entry-gen]
   (gen/fmap ss/series* (gen/vector entry-gen))))

(defn power-series
  "Generates a [[series/PowerSeries]] instance of elements drawn from `entry-gen`.

  `entry-gen` defaults to [[gen/nat]]."
  ([] (power-series gen/nat))
  ([entry-gen]
   (gen/fmap ss/power-series* (gen/vector entry-gen))))

;; ## Vector Set
;;
;; These are used in the implementation of [[sicmutils.differential]].

(defn vector-set
  "Generates a sorted vector of distinct elements drawn from `entry-gen`.
  `entry-gen` must produce comparable elements.

  `entry-gen` defaults to [[gen/nat]]."
  ([] (vector-set gen/nat))
  ([entry-gen]
   (gen/fmap vs/make (gen/set entry-gen))))

;; ## Differential

(defn differential
  "Returns a generator that produces proper instances of [[d/Differential]]."
  ([] (differential real))
  ([coeff-gen]
   (let [term-gen   (gen/let [tags (vector-set gen/nat)
                              coef coeff-gen]
                      (let [tags (if (empty? tags) [0] tags)
                            coef (if (v/zero? coef) 1 coef)]
                        (#'d/make-term tags coef)))]
     (gen/let [terms  (gen/vector term-gen 1 5)
               primal coeff-gen]
       (let [tangent-part (d/from-terms terms)
             ret          (d/d:+ primal tangent-part)]
         (cond (d/differential? ret)          ret
               (d/differential? tangent-part) tangent-part
               :else (d/from-terms [(#'d/make-term [] primal)
                                    (#'d/make-term [0] 1)])))))))

;; ## Polynomials

(defn poly:exponents [arity]
  (->> (gen/vector gen/nat arity)
       (gen/fmap xpt/dense->exponents)))

(defn poly:terms
  ([arity]
   (poly:terms arity small-integral))
  ([arity coeff-gen & opts]
   (let [expt-gen (poly:exponents arity)
         term-gen (gen/tuple expt-gen coeff-gen)]
     (apply gen/vector term-gen opts))))

(defn polynomial
  "Returns a generator that produces instances of [[polynomial.Polynomial]].

  `arity` can be a number or a generator."
  [& {:keys [arity coeffs nonzero?]
      :or {nonzero? true
           arity (gen/fmap inc gen/nat)
           coeffs small-integral}}]
  (letfn [(poly-gen [arity]
            (let [terms (poly:terms arity coeffs)
                  pgen (gen/fmap (fn [terms]
                                   (let [p (poly/make arity terms)]
                                     (if (poly/polynomial? p)
                                       p
                                       (poly/constant arity p))))
                                 terms)]
              (if nonzero?
                (gen/such-that (complement v/zero?) pgen)
                pgen)))]
    (let [arity (if (integer? arity)
                  (gen/return arity)
                  arity)]
      (gen/bind arity poly-gen))))

(defn rational-function
  "Returns a generator that produces instances
  of [[rational-function/RationalFunction]].

  `arity` can be a number or a generator."
  ([]
   (rational-function 1 {} {}))
  ([arity]
   (rational-function arity {} {}))
  ([arity num-opts denom-opts]
   (let [num-opts   (assoc num-opts
                           :arity arity)
         denom-opts (assoc denom-opts
                           :nonzero? true
                           :arity arity)
         n  (apply polynomial (apply concat num-opts))
         d  (apply polynomial (apply concat denom-opts))
         rf (gen/fmap
             (fn [[u v]]
               (rf/->reduced u v))
             (gen/tuple n d))]
     (gen/such-that rf/rational-function? rf))))

;; ## Custom Almost-Equality

(defn- eq-delegate
  "Takes a real number `this` on the left, and checks it for approximate equality
  with:

  - complex numbers by comparing to the real part and checking that `that`'s
    imaginary part is roughly ~0
  - real numbers with the default approximate check behavior
  - falls through to `=` for all other types.

  In CLJS, `this` can be `js/BigInt`, `google.math.Long`, `goog.math.Real`,
  `Fraction` or any of the other types in the numeric tower."
  [this that]
  (cond (c/complex? that) (and (si/*comparator* 0.0 (g/imag-part that))
                               (si/*comparator*
                                (u/double this)
                                (g/real-part that)))
        (v/real? that)    (si/*comparator*
                           (u/double this)
                           (u/double that))
        :else             (v/= this that)))

(extend-protocol si/Approximate
  #?@(:cljs
      [r/ratiotype
       (ish [this that] (eq-delegate this that))

       u/inttype
       (ish [this that] (eq-delegate this that))

       u/longtype
       (ish [this that] (eq-delegate this that))

       js/BigInt
       (ish [this that] (eq-delegate this that))

       number
       (ish [this that] (eq-delegate this that))])

  #?@(:clj
      [Double
       (ish [this that] (eq-delegate this that))

       Float
       (ish [this that] (eq-delegate this that))

       Number
       (ish [this that] (eq-delegate this that))])

  #?(:cljs c/complextype :clj Complex)
  (ish [this that]
    (cond (c/complex? that)
          (and (si/*comparator* (c/real this)
                                (c/real that))
               (si/*comparator* (c/imaginary this)
                                (c/imaginary that)))
          (v/real? that)
          (and (si/*comparator* 0.0 (c/imaginary this))
               (si/*comparator*
                (c/real this) (u/double that)))
          :else (v/= this that)))

  Quaternion
  (ish [this that]
    (cond (quat/quaternion? that)
          (and (si/*comparator* (quat/get-r this) (quat/get-r that))
               (si/*comparator* (quat/get-i this) (quat/get-i that))
               (si/*comparator* (quat/get-j this) (quat/get-j that))
               (si/*comparator* (quat/get-k this) (quat/get-k that)))

          (c/complex? that)
          (and (si/*comparator* 0.0 (quat/get-j this))
               (si/*comparator* 0.0 (quat/get-k this))
               (si/*comparator* (quat/get-r this) (c/real that))
               (si/*comparator* (quat/get-i this) (c/imaginary that)))

          (v/real? that)
          (and (si/*comparator* 0.0 (quat/get-i this))
               (si/*comparator* 0.0 (quat/get-j this))
               (si/*comparator* 0.0 (quat/get-k this))
               (si/*comparator*
                (quat/get-r this) (u/double that)))

          :else (v/= this that))))
