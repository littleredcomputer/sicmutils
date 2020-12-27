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

(ns sicmutils.differential
  (:require [clojure.string :refer [join]]
            [sicmutils.function :as f]
            [sicmutils.generic :as g]
            [sicmutils.util :as u]
            [sicmutils.util.stream :as us]
            [sicmutils.util.vector-set :as uv]
            [sicmutils.value :as v]))

;; ## Calculus of Infinitesimals
;;
;; The idea is that we compute derivatives by passing special
;; "differential objects" [x,dx] through functions. A first approximation to the
;; idea is as follows:
;;
;; f[x,dx] |---> [f(x), Df(x)*dx]
;;
;; Note that the derivative of f at the point x, DF(x), is the coefficient of dx
;; in the result. If we then pass this result through another function, we
;; obtain the chain-rule answer we would hope for.
;;
;; g[f(x), Df(x)*dx] |---> [g(f(x)), DG(f(x))*DF(x)*dx]
;;
;; Thus, we can find the derivative of a composition by this process. We need
;; only define how each of the primitives act on these "differentials" and then
;; we can use ordinary Scheme compositions of these to do the job. See the
;; procedure diff:derivative near the bottom to understand how derivatives are
;; computed given this differential algebra. This idea was discovered by Dan
;; Zuras and Gerald Jay Sussman in 1992. DZ and GJS made the first version of
;; this code during an all nighter in 1992.
;;
;; To expand this idea to work for multiple derivatives of functions of several
;; variables we define an algebra in "infinitesimal space". The objects are
;; multivariate power series in which no incremental has exponent greater than
;; 1. This was worked out in detail by Hal Abelson around 1994, and painfully
;; redone in 1997 by Sussman with the help of Hardy Mayer and Jack Wisdom.
;;
;; A rare and surprising bug was discovered by Alexey Radul in 2011. This was
;; fixed by remapping the infinitesimals for derivatives of functions that
;; returned functions. This was done kludgerously, but it works.
;;
;; ### Data Structures
;;
;; A differential quantity is a typed list of differential terms, representing
;; the power series alluded to earlier. The terms are kept in a sorted order, in
;; ascending order. (Order is the number of incrementals. So dx*dy is higher
;; order than dx or dy.)

;; A differential term is implemented as a pair whose first element is a set of
;; tags and whose second is the coefficient. Let's do the tag set implementation
;; first.

(def tags first)
(def coefficient second)

(defn make-term
  ([coef] [uv/empty-set coef])
  ([tags coef] [tags coef]))

;; Each differential term has a list of tags. The tags represent the
;; incrementals. Roughly, "dx" and "dy" are tags in the terms: 3*dx, 4*dy,
;; 2*dx*dy. There is a tag created for each derivative that is in progress.
;; Since the only use of a tag is to distinguish unnamed incrementals we use
;; positive integers for the tags.

(let [next-tag (atom 0)]
  (defn fresh-tag []
    (swap! next-tag inc)))

;; Tags are small integers. Tag sets are typically of small cardinality. So we
;; experiment with implementing them as small vectors, instead of sorted sets.
;; This implementation lives in [[sicmutils.util.vector-set]], and we use it
;; here.

(defn- tag-in-term?
  "Return true if `t` is in the tag-set of the supplied `term`, false otherwise."
  [term t]
  (uv/contains? (tags term) t))

;; ## Term List Algebra
;;
;; Now, we get to 'terms'. TODO note what is going on... we've multiplied out a
;; bunch of these things, but we can always turn them back into a dual number.
;;
;; From scmutils: "Differential term lists represent a kind of power series, so
;; they can be added and multiplied. It is important to note that when terms are
;; multiplied, no contribution is made if the terms being multiplied have a
;; differential tag in common. Thus dx^2 = zero."
;;
;; TODO also note that Clojure vectors already compare properly, so term lists
;; can be compared nicely now if we compare by just the first terms, not the
;; coefs.... maybe this doesn't matter.

(defn terms:+
  "Iterate and build up the result while preserving order and dropping zero sums."
  [xs ys]
  (loop [xs xs
         ys ys
         result []]
    (cond (empty? xs) (into result ys)
          (empty? ys) (into result xs)
          :else (let [[a-tags a-coef :as a] (first xs)
                      [b-tags b-coef :as b] (first ys)
                      c (compare a-tags b-tags)]
                  (cond
                    (= c 0)
                    (let [r-coef (g/+ a-coef b-coef)]
                      (recur (rest xs)
                             (rest ys)
                             (if (v/zero? r-coef)
                               result
                               (conj result (make-term a-tags r-coef)))))

                    (< c 0)
                    (recur (rest xs) ys (conj result a))

                    :else
                    (recur xs (rest ys) (conj result b)))))))

(defn terms:*
  "Eagerly multiply the two term lists."
  [xs ys]
  (for [[x-tags x-coef] xs
        [y-tags y-coef] ys
        :when (empty? (uv/intersection x-tags y-tags))]
    (make-term (uv/union x-tags y-tags)
               (g/* x-coef y-coef))))

(defn- collect-terms
  "Build a term list up of pairs of tags => coefficients by grouping together and
  summing coefficients paired with the same term list.

  The result will be sorted by term list, and contain no duplicate term lists."
  [tags->coefs]
  (let [terms (for [[tags tags-coefs] (group-by tags tags->coefs)
                    :let [c (transduce (map coefficient) g/+ tags-coefs)]
                    :when (not (v/zero? c))]
                [tags c])]
    (into [] (sort-by tags terms))))

;; ## Differential
;;
;; Now that we have term lists, we can define the real deal, the full thing. As
;; with other types, the step here is to wrap up this thing in a `deftype` so we
;; can customize it.

;; TODO implement ifn, compare, equals for clj, cljs (note that compare needs to
;; compare ONLY by the first element, not the coefficient....  think)

;; TODO note that we are extending scalar here for blah reasons.

(derive ::differential ::v/scalar)

(declare d:= one?)

(deftype Differential [terms]
  ;; TODO handle empty case.
  f/IArity
  (arity [_]
    (f/arity (coefficient (first terms))))

  v/Numerical
  (numerical? [d]
    (v/numerical? (coefficient (first terms))))

  v/Value
  (zero? [this]
    (every? (comp v/zero? coefficient) terms))

  (one? [this] (one? this))
  (identity? [this] (d:one? this))
  (zero-like [_] 0)
  (one-like [_] 1)
  (identity-like [_] 1)
  (freeze [_] `[~'Differential ~@terms])
  (exact? [_] false)

  (kind [_] ::differential)

  Object
  (toString [_] (str "D[" (join " " (map #(join " → " %) terms)) "]"))

  #?(:clj
     (equals [a b] (d:= a b)))

  #?@(:cljs
      [IEquiv
       (-equiv [a b] (d:= a b))

       IPrintWithWriter
       (-pr-writer [x writer _]
                   (write-all writer (.toString x)))]))

#?(:clj
   (defmethod print-method Differential
     [^Differential s ^java.io.Writer w]
     (.write w (.toString s))))

;; ## Accessor Methods

(defn differential?
  "Returns true if the supplied object is an instance of `Differential`, false
  otherwise."
  [dx]
  (instance? Differential dx))

(defn terms
  "For the supplied `Differential` object, returns its vector of terms."
  [dx]
  {:pre [(differential? dx)]}
  (.-terms ^Differential dx))

;; TODO rename this to `->terms` maybe?

(defn- differential->terms
  "Given a differential, returns the vector of DifferentialTerms
  within; otherwise, returns a singleton differential term
  representing d with an empty tag list (unless d is zero, in
  which case we return the empty term list)."
  [dx]
  (cond (differential? dx)
        (filterv (fn [term]
                   (not (v/zero? (coefficient term))))
                 (terms dx))

        (v/zero? dx) []
        :else        [(make-term dx)]))

(defn- terms->differential
  "Returns a differential instance generated from a vector of terms.... TODO note the conditions.

  TODO note that this assumes that they're all properly sorted."
  [terms]
  {:pre [(vector? terms)]}
  (cond (empty? terms) 0

        (and (= (count terms) 1)
             (empty? (tags (first terms))))
        (coefficient (first terms))

        :else (->Differential terms)))

(defn sum->differential
  "The input here is a mapping (loosely defined) between sets of
  differential tags and coefficients.

  The mapping can be an actual map, or just a sequence of pairs. The
  differential tag sets are sequences of integer tags, which should be sorted.

  TODO note that this groups and sums; maybe less efficient than doing it the
  power series way. Check!"
  [terms]
  (terms->differential
   (collect-terms terms)))

;; TODO Now we use them...

(defn max-order-tag
  "From each of the differentials in the sequence ds, find the highest
  order term; then return the greatest tag found in any of these
  terms; i.e., the highest-numbered tag of the highest-order term."
  ([dx]
   (when (differential? dx)
     (let [last-term   (peek (differential->terms dx))
           highest-tag (peek (tags last-term))]
       highest-tag)))
  ([d & ds]
   (letfn [(max-termv [d]
             (if-let [max-order (max-order-tag d)]
               [max-order]
               []))]
     (->> (mapcat max-termv (cons d ds))
          (apply max)))))

(defn- diff:apply
  "If the coefficients are themselves functions, apply them to the args for ALL
  coefficients."
  [diff args]
  (terms->differential
   (mapcat (fn [term]
             (let [result (apply (coefficient term) args)]
               (if (v/zero? result)
                 []
                 (make-term (tags term) result))))
           (differential->terms diff))))

(defn d:+
  "Adds two objects differentially. (One of the objects might not be
  differential; in which case we lift it into a trivial differential
  before the addition.)"
  [dx dy]
  (terms->differential
   (terms:+ (differential->terms dx)
            (differential->terms dy))))

(defn d:*
  "Form the product of the differentials dx and dy."
  [dx dy]
  (sum->differential
   (terms:* (differential->terms dx)
            (differential->terms dy))))

(defn bundle
  "The constructor we care about, finally!"
  ([primal tag]
   (bundle primal 1 tag))
  ([primal tangent tag]
   (let [term (make-term (uv/make [tag])
                         tangent)]
     (d:+ primal (->Differential [term])))))

;; TODO note what is going on with this protocol.

(defprotocol IPerturbed
  (perturbed? [this]
    "This is currently only used by a literal fn... TODO note what this does.")
  (replace-tag [this old new]
    "TODO note why we need this.")
  (extract-tangent [this tag]
    "TODO note!"))

(extend-protocol IPerturbed
  #?(:clj Object :cljs default)
  (perturbed? [_] false)
  (replace-tag [this _ _] this)
  (extract-tangent [_ _] 0)

  Differential
  (perturbed? [_] true)
  (replace-tag [dx oldtag newtag]
    (terms->differential
     (mapv (fn [term]
             (let [tagv (tags term)]
               (if (uv/contains? tagv oldtag)
                 (make-term (-> (tags term)
                                (uv/disj oldtag)
                                (uv/conj newtag))
                            (coefficient term))
                 term)))
           (terms dx))))
  (extract-tangent [dx tag]
    (sum->differential
     (mapcat (fn [term]
               (let [tagv (tags term)]
                 (if (uv/contains? tagv tag)
                   [(make-term (uv/disj tagv tag)
                               (coefficient term))]
                   [])))
             (terms dx)))))

(defn primal-part
  "The differential containing only those terms _without_ the given tag"
  ([dx] (primal-part dx (max-order-tag dx)))
  ([dx tag]
   (if (differential? dx)
     (let [sans-tag? #(not (tag-in-term? % tag))]
       (->> (differential->terms dx)
            (filterv sans-tag?)
            (sum->differential)))
     dx)))

(defn tangent-part
  "The differential containing only those terms _with_ the given tag"
  ([dx] (tangent-part dx (max-order-tag dx)))
  ([dx tag]
   (if (differential? dx)
     (->> (differential->terms dx)
          (filterv #(tag-in-term? % tag))
          (sum->differential))
     0)))

(defn primal-tangent-pair
  "Split the differential into the parts with and without tag and return the
  pair."
  ([dx] (primal-tangent-pair dx (max-order-tag dx)))
  ([dx tag]
   (if-not (differential? dx)
     [dx 0]
     (let [[tangent-terms primal-terms]
           (us/separatev #(tag-in-term? % tag)
                         (differential->terms dx))]
       [(sum->differential primal-terms)
        (sum->differential tangent-terms)]))))

(defn one? [dx]
  (let [[p t] (primal-tangent-pair dx)]
    (and (v/one? p)
         (v/zero? t))))

(defn d:=
  "Returns true if the [[Differential]] instance `a` equals `b`, false otherwise.

  TODO Is this valid? Do we want to compare on differentials... ever? double
  check with the benchmark dataset..."
  [a b]
  {:pre [(differential? a)]}
  (if (differential? b)
    (= (terms a) (terms b))
    (= (primal-part a) b)))

(defn d:compare
  "Comparator that can compare differentials with non-differentials.

  TODO if we do it this way, there's no way it's sound to compare above using
  equals... since they won't match."
  [a b]
  (compare (primal-part a)
           (primal-part b)))

(defn- unary-op [f df:dx]
  (fn [x]
    (let [[p t] (primal-tangent-pair x)]
      (d:+ (f p) (d:* (df:dx p) t)))))

(defn- binary-op [f df:dx df:dy]
  (fn [x y]
    (let [tag     (max-order-tag x y)
          [xe dx] (primal-tangent-pair x tag)
          [ye dy] (primal-tangent-pair y tag)
          a (f xe ye)
          b (if (and (v/number? dx) (v/zero? dx))
              a
              (d:+ a (d:* dx (df:dx xe ye))))]
      (if (and (v/number? dy) (v/zero? dy))
        b
        (d:+ b (d:* (df:dy xe ye) dy))))))

(def ^:private diff-+ (binary-op g/+ (fn [_ _] 1) (fn [_ _] 1)))
(def ^:private diff-- (binary-op g/- (fn [_ _] 1) (fn [_ _] -1)))
(def ^:private diff-* (binary-op g/* (fn [_ y] y) (fn [x _] x)))
(def ^:private diff-div
  (binary-op g/div
             (fn [_ y] (g/invert y))
             (fn [x y] (g/negate (g/divide x (g/square y))))))

(def ^:private sin (unary-op g/sin g/cos))
(def ^:private cos (unary-op g/cos #(g/negate (g/sin %))))
(def ^:private tan (unary-op g/tan #(g/invert (g/square (g/cos %)))))

(def ^:private asin
  (unary-op g/asin #(g/invert (g/sqrt (g/sub 1 (g/square %))))))

(def ^:private acos
  (unary-op g/acos #(g/negate (g/invert (g/sqrt (g/sub 1 (g/square %)))))))

(def ^:private atan
  (unary-op g/atan #(g/invert (g/add 1 (g/square %)))))

(def ^:private atan2
  (binary-op g/atan
             (fn [y x]
               (g/divide x
                         (g/add (g/square x)
                                (g/square y))))
             (fn [y x]
               (g/divide (g/negate y)
                         (g/add (g/square x)
                                (g/square y))))))

(defn- abs [x]
  (let [f (primal-part x)
        func (cond (< f 0) (unary-op (fn [x] x) (fn [_] -1))
                   (> f 0) (unary-op (fn [x] x) (fn [_] 1))
                   (= f 0) (u/illegal "Derivative of g/abs undefined at zero")
                   :else (u/illegal (str "error! derivative of g/abs at" x)))]
    (func x)))

(def ^:private sinh (unary-op g/sinh g/cosh))
(def ^:private cosh (unary-op g/cosh g/sinh))
(def ^:private tanh (unary-op g/tanh #(g/sub 1 (g/square (g/tanh %)))))

(def ^:private sqrt (unary-op g/sqrt #(-> % g/sqrt (g/mul 2) g/invert)))
(def ^:private exp (unary-op g/exp g/exp))
(def ^:private negate (unary-op g/negate (fn [_] -1)))

(def ^:private power
  (binary-op g/expt
             (fn [x y]
               (g/mul y (g/expt x (g/sub y 1))))
             (fn [_ _]
               (u/illegal "can't get there from here"))))

(def ^:private expt
  (binary-op g/expt
             (fn [x y]
               (g/mul y (g/expt x (g/sub y 1))))
             (fn [x y]
               (if (and (v/number? x) (v/zero? y))
                 (if (v/number? y)
                   (if (not (g/negative? y))
                     0
                     (u/illegal "Derivative undefined: expt"))
                   0)
                 (g/* (g/log x) (g/expt x y))))))
(def ^:private log (unary-op g/log g/invert))

(defn- defbinary [generic-op differential-op]
  (doseq [signature [[::differential ::differential]
                     [::v/scalar ::differential]
                     [::differential ::v/scalar]]]
    (defmethod generic-op signature [a b] (differential-op a b))))

(defn- defunary [generic-op differential-op]
  (defmethod generic-op [::differential] [a] (differential-op a)))

(defmethod g/expt [::differential ::v/number] [d n] (power d n))
(defbinary g/expt expt)

(defbinary g/add diff-+)
(defbinary g/sub diff--)
(defbinary g/mul diff-*)
(defbinary g/div diff-div)

(defunary g/log log)
(defunary g/exp exp)
(defunary g/abs abs)
(defunary g/sqrt sqrt)

(defunary g/sin sin)
(defunary g/cos cos)
(defunary g/tan tan)

(defunary g/asin asin)
(defunary g/acos acos)
(defunary g/atan atan)
(defbinary g/atan atan2)

(defunary g/sinh sinh)
(defunary g/cosh cosh)
(defunary g/tanh tanh)

(defunary g/negate negate)
(defunary g/invert #(diff-div 1 %))
(defunary g/square #(diff-* % %))
(defunary g/cube #(diff-* % (diff-* % %)))

(defbinary g/dot-product diff-*)
