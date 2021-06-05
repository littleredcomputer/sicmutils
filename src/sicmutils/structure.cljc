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

(ns sicmutils.structure
  (:require [clojure.string :refer [join]]
            [sicmutils.collection]
            [sicmutils.differential :as d]
            [sicmutils.expression :as x]
            [sicmutils.function :as f]
            [sicmutils.generic :as g]
            [sicmutils.operator :as o]
            [sicmutils.util :as u]
            [sicmutils.util.aggregate :as ua]
            [sicmutils.numsymb]
            [sicmutils.value :as v])
  #?(:clj
     (:import (clojure.lang Associative
                            AFn IFn
                            IPersistentVector IReduce
                            IObj
                            Indexed Sequential))))

(def ^:dynamic *allow-incompatible-multiplication* true)

;; Type Declarations

(def ^:no-doc orientation->symbol
  {::up 'up ::down 'down})

(def ^:no-doc orientation->separator
  {::up "↑" ::down "_"})

(def opposite-orientation
  {::up ::down ::down ::up})

(derive ::up ::structure)
(derive ::down ::structure)
(derive #?(:clj IPersistentVector :cljs PersistentVector) ::up)

;; Structures can interact with functions.
(derive ::structure ::f/cofunction)
(derive ::structure ::o/co-operator)

;; ## Utilities
;;
;; These are related to structures, but probably need a better home.
(defn kronecker
  "Returns `1` if `i`== `j`, `0` otherwise."
  [i j]
  (if (== i j) 1 0))

(defn basis-unit
  "Returns a basis sequence of `n` 0s, with `1` in the `i`th position.

  If `n` is not supplied returns an infinite sequence."
  ([i] (map (partial kronecker i)
            (range)))
  ([n i] (take n (basis-unit i))))

;; ## Structure Type Definition

(declare s:= mapr)

(deftype Structure [orientation v m]
  v/Value
  (zero? [_] (every? v/zero? v))
  (one? [_] false)
  (identity? [_] false)
  (zero-like [_] (Structure. orientation (v/zero-like v) m))
  (one-like [o] (u/unsupported (str "one-like: " o)))
  (identity-like [o] (u/unsupported (str "identity-like: " o)))
  (exact? [_] (every? v/exact? v))
  (freeze [_] `(~(orientation orientation->symbol) ~@(map v/freeze v)))
  (kind [_] orientation)

  f/IArity
  (arity [_]
    (f/seq-arity v))

  d/IPerturbed
  (perturbed? [_] (boolean (some d/perturbed? v)))
  (replace-tag [s old new] (mapr #(d/replace-tag % old new) s))
  (extract-tangent [s tag] (mapr #(d/extract-tangent % tag) s))

  #?@(:clj
      [Object
       (equals [this that] (s:= this that))
       (toString [_] (str "("
                          (orientation orientation->symbol)
                          " "
                          (join " " (map pr-str v))
                          ")"))

       IObj
       (meta [_] m)
       (withMeta [_ m] (Structure. orientation v m))

       Sequential

       Associative
       (assoc [_ k entry] (Structure. orientation (assoc v k entry) m))
       (containsKey [_ k] (.containsKey ^Associative v k))
       (entryAt [_ k] (.entryAt ^Associative v k))
       (cons [_ o] (Structure. orientation (conj v o) m))
       (count [_] (.count ^Associative v))
       (seq [_] (.seq ^Associative v))
       (valAt [_ key] (.valAt ^Associative v key))
       (valAt [_ key default] (.valAt ^Associative v key default))
       (empty [_] (Structure. orientation [] nil))
       (equiv [this that] (s:= this that))

       Indexed
       (nth [_ key] (.nth ^Indexed v key))
       (nth [_ key default] (.nth ^Indexed v key default))

       IReduce
       (reduce [_ f] (.reduce ^IReduce v f))
       (reduce [_ f start] (.reduce ^IReduce v f start))

       IFn
       (invoke [_]
               (Structure. orientation (mapv #(%) v) m))
       (invoke [_ a]
               (Structure. orientation (mapv #(% a) v) m))
       (invoke [_ a b]
               (Structure. orientation (mapv #(% a b) v) m))
       (invoke [_ a b c]
               (Structure. orientation (mapv #(% a b c) v) m))
       (invoke [_ a b c d]
               (Structure. orientation (mapv #(% a b c d) v) m))
       (invoke [_ a b c d e]
               (Structure. orientation (mapv #(% a b c d e) v) m))
       (invoke [_ a b c d e f]
               (Structure. orientation (mapv #(% a b c d e f) v) m))
       (invoke [_ a b c d e f g]
               (Structure. orientation (mapv #(% a b c d e f g) v) m))
       (invoke [_ a b c d e f g h]
               (Structure. orientation (mapv #(% a b c d e f g h) v) m))
       (invoke [_ a b c d e f g h i]
               (Structure. orientation (mapv #(% a b c d e f g h i) v) m))
       (invoke [_ a b c d e f g h i j]
               (Structure. orientation (mapv #(% a b c d e f g h i j) v) m))
       (invoke [_ a b c d e f g h i j k]
               (Structure. orientation (mapv #(% a b c d e f g h i j k) v) m))
       (invoke [_ a b c d e f g h i j k l]
               (Structure. orientation (mapv #(% a b c d e f g h i j k l) v) m))
       (invoke [_ a b c d e f g h i j k l m-arg]
               (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg) v) m))
       (invoke [_ a b c d e f g h i j k l m-arg n]
               (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n) v) m))
       (invoke [_ a b c d e f g h i j k l m-arg n o]
               (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o) v) m))
       (invoke [_ a b c d e f g h i j k l m-arg n o p]
               (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o p) v) m))
       (invoke [_ a b c d e f g h i j k l m-arg n o p q]
               (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o p q) v) m))
       (invoke [_ a b c d e f g h i j k l m-arg n o p q r]
               (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o p q r) v) m))
       (invoke [_ a b c d e f g h i j k l m-arg n o p q r s]
               (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o p q r s) v) m))
       (invoke [_ a b c d e f g h i j k l m-arg n o p q r s t]
               (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o p q r s t) v) m))
       (invoke [_ a b c d e f g h i j k l m-arg n o p q r s t rest]
               (Structure. orientation (mapv #(apply % a b c d e f g h i j k l m-arg n o p q r s t rest) v) m))
       (applyTo [s xs] (AFn/applyToHelper s xs))]

      :cljs
      [Object
       (toString [_] (str "("
                          (orientation orientation->symbol)
                          " " (join " " (map pr-str v))
                          ")"))

       IMeta
       (-meta [_] m)

       IWithMeta
       (-with-meta [_ m] (Structure. orientation v m))

       IPrintWithWriter
       (-pr-writer [x writer _]
                   (write-all writer (.toString x)))

       ICollection
       (-conj [_ item] (Structure. orientation (-conj v item) m))

       IEmptyableCollection
       (-empty [_] (Structure. orientation [] m))

       ISequential

       IEquiv
       (-equiv [this that] (s:= this that))

       ISeqable
       (-seq [_] (-seq v))

       ICounted
       (-count [_] (-count v))

       IIndexed
       (-nth [_ n] (-nth v n))
       (-nth [_ n not-found] (-nth v n not-found))

       ILookup
       (-lookup [_ k] (-lookup v k))
       (-lookup [_ k not-found] (-lookup v k not-found))

       IAssociative
       (-assoc [_ k entry] (Structure. orientation (-assoc v k entry) m))
       (-contains-key? [_ k] (-contains-key? v k))

       IFind
       (-find [_ n] (-find v n))

       IReduce
       (-reduce [_ f] (-reduce v f))
       (-reduce [_ f start] (-reduce v f start))

       IFn
       (-invoke [_]
                (Structure. orientation (mapv #(%) v) m))
       (-invoke [_ a]
                (Structure. orientation (mapv #(% a) v) m))
       (-invoke [_ a b]
                (Structure. orientation (mapv #(% a b) v) m))
       (-invoke [_ a b c]
                (Structure. orientation (mapv #(% a b c) v) m))
       (-invoke [_ a b c d]
                (Structure. orientation (mapv #(% a b c d) v) m))
       (-invoke [_ a b c d e]
                (Structure. orientation (mapv #(% a b c d e) v) m))
       (-invoke [_ a b c d e f]
                (Structure. orientation (mapv #(% a b c d e f) v) m))
       (-invoke [_ a b c d e f g]
                (Structure. orientation (mapv #(% a b c d e f g) v) m))
       (-invoke [_ a b c d e f g h]
                (Structure. orientation (mapv #(% a b c d e f g h) v) m))
       (-invoke [_ a b c d e f g h i]
                (Structure. orientation (mapv #(% a b c d e f g h i) v) m))
       (-invoke [_ a b c d e f g h i j]
                (Structure. orientation (mapv #(% a b c d e f g h i j) v) m))
       (-invoke [_ a b c d e f g h i j k]
                (Structure. orientation (mapv #(% a b c d e f g h i j k) v) m))
       (-invoke [_ a b c d e f g h i j k l]
                (Structure. orientation (mapv #(% a b c d e f g h i j k l) v) m))
       (-invoke [_ a b c d e f g h i j k l m-arg]
                (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg) v) m))
       (-invoke [_ a b c d e f g h i j k l m-arg n]
                (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n) v) m))
       (-invoke [_ a b c d e f g h i j k l m-arg n o]
                (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o) v) m))
       (-invoke [_ a b c d e f g h i j k l m-arg n o p]
                (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o p) v) m))
       (-invoke [_ a b c d e f g h i j k l m-arg n o p q]
                (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o p q) v) m))
       (-invoke [_ a b c d e f g h i j k l m-arg n o p q r]
                (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o p q r) v) m))
       (-invoke [_ a b c d e f g h i j k l m-arg n o p q r s]
                (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o p q r s) v) m))
       (-invoke [_ a b c d e f g h i j k l m-arg n o p q r s t]
                (Structure. orientation (mapv #(% a b c d e f g h i j k l m-arg n o p q r s t) v) m))
       (-invoke [_ a b c d e f g h i j k l m-arg n o p q r s t rest]
                (Structure. orientation (mapv #(apply % a b c d e f g h i j k l m-arg n o p q r s t rest) v) m))
       ]))

#?(:clj
   (defmethod print-method Structure [^Structure s ^java.io.Writer w]
     (.write w (.toString s))))

(do (ns-unmap 'sicmutils.structure '->Structure)
    (defn ->Structure
      "Positional factory function for [[Structure]].

  The final argument `m` defaults to nil if not supplied."
      ([orientation v]
       (Structure. orientation v nil))
      ([orientation v m]
       (Structure. orientation v m))))

;; ## Component Accessors

(defn structure->vector
  "Return the structure `s` in unoriented vector form."
  [s]
  (cond (vector? s)             s
        (instance? Structure s) (.-v ^Structure s)
        :else
        (u/illegal (str "non-structure supplied: " s))))

(defn orientation
  "Returns the orientation of `s`, either `::up` or `::down`. Defaults to `::up`,
  even for non-structures."
  [s]
  (if (instance? Structure s)
    (.-orientation ^Structure s)
    ::up))

(defn- s:count
  "Returns the count for sequential `s`, `1` otherwise."
  [s]
  (if (sequential? s)
    (count s)
    1))

(defn dimension
  "If `s` is sequential, returns its dimension, ie, the total number of
  non-sequential entries in the structure. Else, returns 1."
  [s]
  (if (sequential? s)
    (-> s flatten count)
    1))

(defn- s:nth
  "Structure-specific version of `nth`; acts as [[clojure.core/nth]] for
  structural things.

  For non-sequential things, if `i` is `0`, acts as identity. Throws otherwise."
  [s i]
  (cond (sequential? s) (nth s i)
        (= i 0)         s
        :else
        (u/illegal
         (str "non-sequential s:nth not supported: "
              s " with index != 0: " i))))

(defn component
  "Given an access chain (a sequence of indices), return a function that accepts a
  structure and returns the element at the specified access chain."
  [& indices]
  #(get-in % indices))

;; ## Structure Predicates
;;
;; `::down` instances should never be equal to collections, or `::up`. By
;; default in Clojure, all collections compare as if they were sequences, so an
;; up can't equal a down... but a vector would! This change fixes that.

(defmethod v/= [::down ::up] [_ _] false)
(defmethod v/= [::up ::down] [_ _] false)
(defmethod v/= [::down v/seqtype] [_ _] false)
(defmethod v/= [v/seqtype ::down] [_ _] false)
(prefer-method v/= [::up ::down] [v/seqtype ::down])
(prefer-method v/= [::down ::up] [::down v/seqtype])

(defn- s:=
  "Returns true if the supplied structure `this` is equal to the argument on the
  right, false otherwise.

  Structures are equal to:

  - other structures that are deep-equal, including orientation
  - other sequences (only for `::up` structures) - the outer sequence is treated
    as an `::up` structure"
  [^Structure this that]
  (cond (instance? Structure that)
        (let [^Structure s that]
          (and (= (.-orientation this)
                  (.-orientation s))
               (v/= (.-v this)
                    (.-v s))))

        (= (.-orientation this) ::up)
        (cond (vector? that)   (v/= (.-v this) that)
              (seqable? that) (v/= (seq this) (seq that))
              :else false)
        :else false))

(defn structure?
  "Returns `true` if `s` is a structure, false otherwise. (Vectors are treated as
  up structures.)"
  [s]
  (or (instance? Structure s)
      (vector? s)))

(defn up?
  "Returns `true` if `s` is an `up` structure, false otherwise."
  [s]
  (or (vector? s)
      (and (instance? Structure s)
           (= ::up (.-orientation ^Structure s)))))

(defn down?
  "Returns `true` if `s` is a `down` structure, false otherwise."
  [s]
  (and (instance? Structure s)
       (= ::down (.-orientation ^Structure s))))

(defn valid-orientation?
  "Returns true if the supplied orientation lives in the set of allowed
  orientations, false otherwise."
  [o]
  (contains? #{::up ::down} o))

(defn same-orientation?
  "Returns true if the supplied structures have the same orientation, false
  otherwise."
  [s t]
  (= (orientation s)
     (orientation t)))

;; ## Constructors

(defn make
  "Generate a structure with the supplied orientation, given some sequence `xs`"
  [orientation xs]
  (let [xs (if (vector? xs) xs (into [] xs))]
    (->Structure orientation xs)))

(defn up*
  "Construct an up (contravariant) tuple from the supplied sequence. For a
  variadic version, see [[up]]."
  [xs]
  (make ::up xs))

(defn vector->up
  "Form an up-tuple from a vector.

  NOTE that this is an alias of [[up*]] that is more restrictive, in that it
  only accepts a vector. Use [[up*]] if you'd like to pass an arbitrary
  sequence. (If you pass a vector to [[up*]]) it will be just as efficient."
  [v]
  {:pre [(vector? v)]}
  (->Structure ::up v))

(defn up
  "Construct an up (contravariant) tuple from the arguments.

  Variadic version of [[up*]]."
  [& xs]
  (up* xs))

(defn down*
  "Construct a down (covariant) tuple from the supplied sequence. For a
  variadic version, see [[down]]."
  [xs]
  (make ::down xs))

(defn vector->down
  "Form a down-tuple from a vector.

  NOTE that this is an alias of [[down*]] that is more restrictive, in that it
  only accepts a vector. Use [[down*]] if you'd like to pass an arbitrary
  sequence. (If you pass a vector to [[down*]]) it will be just as efficient."
  [v]
  {:pre [(vector? v)]}
  (->Structure ::down v))

(defn down
  "Construct a down (covariant) tuple from the arguments. Variadic version
  of [[down*]]."
  [& xs]
  (make ::down xs))

(defn same
  "Returns a structure containing `xs` with the same orientation as `s`."
  [s xs]
  (make (orientation s) xs))

(defn opposite
  "For a non-[[Structure]] `s`, the single-arity case acts as [[identity]]. For
  a [[Structure]], returns an identical structure with its orientation
  reversed (up becomes down, down becomes up).

  NOTE that a vector is interpreted as an `up` structure, so:

  (opposite [1 2 3])
  ;;=> (down 1 2 3)

  The two-arity case returns a new [[Structure]] of opposite orientation to `s`
  with the contents of the sequence `xs`."
  ([s]
   (if (structure? s)
     (opposite s (structure->vector s))
     s))
  ([s xs]
   (let [o (opposite-orientation
            (orientation s))]
     (make o xs))))

(defn generate
  "Generate a structure with the given `orientation` whose elements are

  (f i)

  where i ranges from `[0..dimension)`."
  [dimension orientation f]
  {:pre [(valid-orientation? orientation)]}
  (->Structure orientation (mapv f (range dimension))))

(defn literal
  "Generates a structure of the specified `orientation` and dimension `size`
  populated by symbolic entries, each prefixed by the supplied symbol `sym`.

  For example:

  (= (literal 'x 3 ::s/up)
     (up 'x↑0 'x↑1 'x↑2))

  See [[literal-up]] and [[literal-down]] for constructors with baked in
  orientations."
  [sym size orientation]
  {:pre [(valid-orientation? orientation)]}
  (let [separator (orientation->separator orientation)
        prefix    (str sym separator)]
    (generate size orientation
              (fn [i]
                (symbol (str prefix i))))))

(defn literal-up
  "Generates an `up` structure of dimension `size` populated by symbolic entries,
  each prefixed by the supplied symbol `sym`.

  For example:

  ```clojure
  (= (literal-up 'x 3)
     (up 'x↑0 'x↑1 'x↑2))
  ```"
  [sym size]
  (literal sym size ::up))

(defn literal-down
  "Generates a `down` structure of dimension `size` populated by symbolic entries,
  each prefixed by the supplied symbol `sym`.

  For example:

  ```clojure
  (= (literal-down 'x 3)
     (down 'x_0 'x_1 'x_2))
  ```"
  [sym size]
  (literal sym size ::down))

;; ## Structure Mappers, Aggregators
;;
;; The following functions only make sense if, when there is more than one
;; structure they are all isomorphic.

(defn- sum:l
  "Returns the sum of all values generated by mapping `f` across the same-indexed
  entries of all supplied structures, one level deep."
  [f [s :as structs]]
  (ua/generic-sum (fn [i]
                    (let [xs (map #(s:nth % i) structs)]
                      (apply f xs)))
                  0
                  (count s)))

(defn- sum:r:l
  "Accepts a function `f` and a sequence of isomorphic `structures`; returns the
  sum of the values returned from applying `f` to each associated set of entries
  in each input structure."
  [f structures]
  (sum:l (fn [& elements]
           (if (structure? (first elements))
             (sum:r:l f elements)
             (apply f elements)))
         structures))

(defn sumr
  "Given some function `f` and any number of isomorphic `structures`,
  returns the sum of the results of applying `f` to each associated set of
  entries in each `structure`."
  [f & structures]
  (sum:r:l f structures))

(defn- map:l
  "Returns a new structure generated by mapping `f` across the same-indexed
  entries of all supplied structures, one level deep."
  [f [s :as structs]]
  (if (structure? s)
    (generate (count s)
              (orientation s)
              (fn [i]
                (let [xs (map #(s:nth % i) structs)]
                  (apply f xs))))
    (apply f structs)))

(defn- map:r:l
  "Accepts some function `f` and a sequence of isomorphic `structures`; returns a
  structure of the same shape, with `f` applied to the associated entry of each
  input structure."
  [f structures]
  (map:l (fn [& elements]
           (if (structure? (first elements))
             (map:r:l f elements)
             (apply f elements)))
         structures))

(defn mapr
  "Return a structure with the same shape as s but with f applied to each
  primitive (that is, not structural) component."
  [f & structures]
  (map:r:l f structures))

(defn map-chain
  "Returns a new structure of equivalent shape to `s`, generated by applying `f`
  to three arguments:

  - the entry in the structure
  - a vector of its 'access chain', ie, the path you'd pass
    to [[clojure.core/get-in]] to access the entry
  - a vector of orientations associated with each index in the access chain

  For example:

  ```clojure
  (doall (map-chain print (s/down (s/up 1 2) (s/up 3 4))))

  1 [0 0] [:s/down :s/up]
  2 [0 1] [:s/down :s/up]
  3 [1 0] [:s/down :s/up]
  4 [1 1] [:s/down :s/up]
  ```"
  [f s]
  (letfn [(walk [s chain orientations]
            (if (structure? s)
              (let [o (orientation s)]
                (generate (count s)
                          (orientation s)
                          (fn [i]
                            (walk (s:nth s i)
                                  (conj chain i)
                                  (conj orientations o)))))
              (f s chain orientations)))]
    (walk s [] [])))

(defn structure->access-chains
  "Return a structure of the same shape as `s` whose elements are access chains
  corresponding to position of each element (i.e., the sequence of indices
  needed to address that element via [[get-in]]).

  Each access chain has the sequence of orientations (`::s/up`, `::s/down`)
  associated with each step attached to it as metadata, under an `:orientations`
  key. Use this if the orientation of the indices matters."
  [s]
  (when (structure? s)
    (map-chain (fn [_ chain orientations]
                 ;; subtle (I'm afraid). Here is where we put
                 ;; the access chain into the new structure.
                 ;; But if we put it in as a vector, that would
                 ;; introduce a new layer of structure since
                 ;; vectors are considered up-tuples. So we
                 ;; have to turn it into a seq, which will
                 ;; forfeit structure-nature.
                 (-> (seq chain)
                     (with-meta {:orientations orientations})))
               s)))

(defn structure->prototype
  "Accepts

  - some symbolic (or string) `name`
  - a structure `s`

  and returns a new structure of identical shape, with symbolic entries like
  `'x↑0_1` that show their access chain with proper orientations for each step."
  [name s]
  (mapr (fn [chain]
          (let [separators (->> (meta chain)
                                (:orientations)
                                (map orientation->separator))
                path-seq   (map str separators chain)]
            (symbol
             (apply str name path-seq))))
        (structure->access-chains s)))

(defn unflatten
  "Given:

  - a sequence of `values`
  - a model `struct`

  Returns a new structure generated by unpacking `values` into a structure with
  the same shape as `struct`."
  ([values struct]
   (unflatten same values struct))
  ([constructor values struct]
   (letfn [(u [values struct]
             (if (structure? struct)
               (let [[values' struct']
                     (reduce (fn [[values struct] element]
                               (let [[values' struct'] (u values element)]
                                 [values' (conj struct struct')]))
                             [values []]
                             struct)]
                 [values' (constructor struct struct')])
               [(rest values) (first values)]))]
     (second (u values struct)))))

(defn transpose
  "Returns a structure with the same shape as `s`, with all orientations
  inverted."
  [s]
  (if (structure? s)
    (->Structure
     (opposite-orientation (orientation s))
     (mapv transpose (structure->vector s)))
    s))

(defn transpose-outer
  "Returns a new structure with the same orientation as the first element of `s`,
  filled with elements of the same orientation as `s`.

  Each element is generating by taking the first element of each entry in `s`,
  the the second, etc... In that sense this is similar to a traditional matrix
  transpose.

  A comment from `scmutils` states:

  'used only in symmetrize-Christoffel in
  src/calculus/covariant-derivative.scm.'"
  [s]
  (let [o (orientation s)]
    (map:l (fn [& xs]
             (make o xs))
           s)))

(defn typical-object
  "Returns a structure of the same shape and orientation as `s`, generated by
  substituting gensymmed symbols in for each entry."
  [s]
  (mapr (fn [_] (gensym 'x)) s))

(defn compatible-zero
  "Returns a structure compatible for multiplication with `s` down to 0."
  [s]
  (v/zero-like
   (transpose s)))

(def ^{:doc "Alias for [[compatible-zero]]."}
  dual-zero
  compatible-zero)

(defn compatible-shape
  "Returns a structure compatible for multiplication with `s` down to a scalar,
  with the slots filled with gensyms."
  [s]
  (typical-object
   (transpose s)))

(defn- structure*scalar
  "Returns a structure generated by multiplying every element of `v` by `s` (on
  the right)."
  [v s]
  (same v (map #(g/* % s) v)))

(defn- scalar*structure
  "Returns a structure generated by multiplying every element of `v` by `s` (on
  the left)."
  [s v]
  (same v (map #(g/* s %) v)))

(defn ^:no-doc compatible-for-contraction?
  "Returns `true` if `s` and `t` are

  - of opposite orientation
  - equal in length
  - are full of elements also compatible for contraction (also true if either
    pair is NOT a structure)

  false otherwise."
  [s t]
  (and (not (same-orientation? s t))
       (= (count s) (count t))
       (every? (fn [[l r]]
                 (or (not (structure? l))
                     (not (structure? r))
                     (compatible-for-contraction? l r)))
               (map vector s t))))

(defn vector-dot-product
  "Returns the (vector) dot product of `v1` and `v2`; this is equivalent to the sum
  of the pairwise product of each entry.

  The arguments must have identical length, and all pairwise entries must be
  compatible via [[g/*]]."
  [v1 v2]
  (assert (= (count v1) (count v2))
          (str "Not same dimension -- v:dot-product"
               v1 ", " v2))
  (apply g/+ (map g/* v1 v2)))

(defn vector-inner-product
  "Returns the (vector) inner product of `v1` and `v2`; this is equivalent to the
  sum of the pairwise product of each entry.

    This is equivalent to [[vector-dot-product]] with every element of `v1`
  transformed into its complex conjugate.

  The arguments must have identical length, and all pairwise entries must be
  compatible via [[g/*]]."
  [v1 v2]
  (vector-dot-product
   (g/conjugate v1) v2))

(defn ^:no-doc s:*
  "If `s` and `t` are compatible for contraction, returns their vector dot
  product.

  Else, returns a new structure generated by multiplying `s` by every element of
  `t`, following the usual multiplicating rules for whatever entry type exists.

  If `*allow-incompatible-multiplication*` is set to false, `s` and `t` will be
  checked for:

  - opposite orientations,
  - every element of `t` must be compatible for multiplication with all of `s`.

  If those tests fail, `s:*` will throw."
  [s t]
  (cond (compatible-for-contraction? s t)
        (vector-dot-product s t)

        (or *allow-incompatible-multiplication*
            (and (not (same-orientation? s t))
                 (every? (fn [elem]
                           (compatible-for-contraction? s elem))
                         t)))
        (scalar*structure s t)

        :else (u/illegal
               (str "Incompatible multiplication: " s t))))

;; NOTE hmmm. why not do the repeated-squaring trick here? perhaps structures
;; are not typically raised to high exponents.

(defn- expt
  "Raise the structure `s` to the nth power."
  [s n]
  (let [one (v/one-like n)]
    (cond (v/one? n) s
          (> n one) (g/* s (g/expt s (g/- n one)))
          :else (u/arithmetic-ex (str "Cannot: " `(expt ~s ~n))))))

(defn- dot-product
  "Returns the structural dot product of the compatible structures `s` and
  `t`.

  To be compatible, both structures must have the same structure."
  [s t]
  (let [s' (transpose s)]
    (if (compatible-for-contraction? s' t)
      (vector-dot-product s' t)
      (u/illegal (str "incompatible structures: dot-product "
                      s ", " t)))))

(defn- inner-product
  "Returns the structural inner product of the compatible structures `s` and `t`.
  This is equivalent to [[dot-product]] with every element of `s` transformed
  into its complex conjugate.

  To be compatible, both structures must be of the same orientation and
  dimension. The internal structures currently do NOT have to match."
  [s t]
  (dot-product (g/conjugate s) t))

(defn- outer-product
  "The outer product of s and t is the structure `struct1` with each element at
  the first level multiplied by all of `struct2`, following the usual structure
  multiplication rules."
  [struct2 struct1]
  (letfn [(xform [s1]
            (mapr (fn [s2]
                    (g/* s1 s2))
                  struct2))]
    (mapr xform struct1)))

(defn- cross-product
  "Returns the cross product of structures of length 3. Input orientations are
  ignored; result is an up-tuple."
  [s t]
  (when (or (not= (count s) 3)
            (not= (count t) 3))
    (u/illegal "cross product only works on two elements of ^3"))
  (let [[s0 s1 s2] s
        [t0 t1 t2] t]
    (up (g/- (g/* s1 t2) (g/* s2 t1))
        (g/- (g/* s2 t0) (g/* s0 t2))
        (g/- (g/* s0 t1) (g/* s1 t0)))))

;; ## Generic Method Installation

(defn- elementwise
  "Given a binary operator and two structures of the same size, return
  a structure with the same orientation as the first formed from the
  elementwise binary operation between corresponding elements of the
  structures."
  [op s t]
  (if (= (count s) (count t))
    (->Structure (orientation s) (mapv op s t))
    (u/arithmetic-ex (str op " provided arguments of differing length"))))

(defmethod g/add [::down ::down] [a b] (elementwise g/+ a b))
(defmethod g/add [::up ::up] [a b] (elementwise g/+ a b))

(defmethod g/negate [::structure] [a] (mapr g/negate a))
(defmethod g/sub [::down ::down] [a b] (elementwise g/- a b))
(defmethod g/sub [::up ::up] [a b] (elementwise g/- a b))

(defmethod g/mul [::structure ::structure] [a b] (s:* a b))
(defmethod g/mul [::structure ::v/scalar] [a b] (structure*scalar a b))
(defmethod g/mul [::v/scalar ::structure] [a b] (scalar*structure a b))

(defmethod g/div [::structure ::v/scalar] [a b] (structure*scalar a (g/invert b)))

;; NOTE: structures extend `::f/cofunction`, so when you multiply a function by
;; a structure, the multiplication is deferred to multiplication between the
;; structure and the function's return value.
;;
;; This is NOT the case with operator / structure multiplication. Operators push
;; their multiplication inside of the structure; the return value is a structure
;; of the same shape.

(defmethod g/mul [::o/operator ::structure] [op s]
  (same s (map #(g/* op %) s)))

(defmethod g/mul [::structure ::o/operator] [s op]
  (same s (map #(g/* % op) s)))

(defmethod g/square [::structure] [a] (dot-product a a))
(defmethod g/cube [::structure] [a] (s:* a (s:* a a)))
(defmethod g/expt [::structure ::v/integral] [a b] (expt a b))
(defmethod g/simplify [::structure] [a]
  (mapr g/simplify a))

(defmethod g/magnitude [::structure] [a]
  (g/sqrt (inner-product a a)))

(defmethod g/abs [::structure] [a]
  (g/sqrt (dot-product a a)))

;; NOTE: `g/make-rectangular` and `g/make-polar` _should_ check that both
;; dimensions match all the way down, but they currently don't. Use with that in
;; mind!

(defmethod g/make-rectangular [::up ::up] [a b]
  (mapr g/make-rectangular a b))

(defmethod g/make-rectangular [::down ::down] [a b]
  (mapr g/make-rectangular a b))

(defmethod g/make-polar [::up ::up] [a b]
  (mapr g/make-polar a b))

(defmethod g/make-polar [::down ::down] [a b]
  (mapr g/make-polar a b))

(defmethod g/real-part [::structure] [m] (mapr g/real-part m))
(defmethod g/imag-part [::structure] [m] (mapr g/imag-part m))
(defmethod g/conjugate [::structure] [a] (mapr g/conjugate a))

(defmethod g/transpose [::structure] [a] (transpose a))
(defmethod g/dimension [::structure] [a] (dimension a))
(defmethod g/dot-product [::structure ::structure] [a b] (dot-product a b))
(defmethod g/inner-product [::structure ::structure] [a b] (inner-product a b))
(defmethod g/outer-product [::structure ::structure] [a b] (outer-product a b))
(defmethod g/cross-product [::up ::up] [a b] (cross-product a b))
