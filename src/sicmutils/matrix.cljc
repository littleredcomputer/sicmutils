;
; Copyright © 2017 Colin Smith.
; This work is based on the Scmutils system of MIT/GNU Scheme:
; Copyright © 2002 Massachusetts Institute of Technology
;
; This is free software;  you can redistribute it and/or modify
; it under the terms of the GNU General Public License as published by
; the Free Software Foundation; either version 3 of the License, or (at
; your option) any later version.
;
; This software is distributed in the hope that it will be useful, but
; WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
; General Public License for more details.
;
; You should have received a copy of the GNU General Public License
; along with this code; if not, see <http://www.gnu.org/licenses/>.
;

(ns sicmutils.matrix
  "This namespace contains an implementation of a [[Matrix]] datatype and various
  operations for creating and working with [[Matrix]] instances.

  [[sicmutils.matrix]] also extends many SICMUtils generic operations
  to the [[Matrix]] datatype."
  (:refer-clojure :rename {get-in core-get-in
                           some core-some}
                  #?@(:cljs [:exclude [get-in some]]))
  (:require [sicmutils.differential :as d]
            [sicmutils.function :as f]
            [sicmutils.generic :as g]
            [sicmutils.series :as series]
            [sicmutils.structure :as s]
            [sicmutils.util :as u]
            [sicmutils.util.aggregate :as ua]
            [sicmutils.value :as v])
  #?(:clj
     (:import (clojure.lang Associative AFn IFn Sequential))))

(declare fmap identity-like identity? m:=)

(derive ::square-matrix ::matrix)
(derive ::column-matrix ::matrix)
(derive ::row-matrix ::matrix)
(derive ::matrix ::f/cofunction)

(deftype Matrix [r c v]
  v/Value
  (zero? [_] (every? #(every? v/zero? %) v))
  (one? [_] false)
  (identity? [m] (identity? m))
  (zero-like [this] (fmap v/zero-like this))
  (one-like [this] (identity-like this))
  (identity-like [this] (identity-like this))

  (freeze [_] (if (= c 1)
                `(~'column-matrix ~@(map (comp v/freeze first) v))
                `(~'matrix-by-rows ~@(map v/freeze v))))
  (exact? [_] (every? #(every? v/exact? %) v))
  (kind [_] (cond (= r c) ::square-matrix
                  (= r 1) ::row-matrix
                  (= c 1) ::column-matrix
                  :else ::matrix))

  d/IPerturbed
  (perturbed? [_] (boolean (core-some d/perturbed? v)))
  (replace-tag [M old new] (fmap #(d/replace-tag % old new) M))
  (extract-tangent [M tag] (fmap #(d/extract-tangent % tag) M))

  f/IArity
  (arity [_] (transduce (map f/seq-arity) f/combine-arities v))

  #?@(:clj
      [Object
       (equals [this that] (m:= this that))
       (toString [_] (pr-str v))

       Sequential

       Associative
       (assoc [_ k entry] (Matrix. r c (assoc v k entry)))
       (containsKey [_ k] (contains? v k))
       (entryAt [_ k] (.entryAt ^Associative v k))
       (count [_] (count v))
       (seq [_] (seq v))
       (valAt [_ key] (get v key))
       (valAt [_ key default] (get v key default))
       (empty [this] (fmap v/zero-like this))
       (equiv [this that] (m:= this that))

       IFn
       (invoke [_ a]
               (Matrix. r c (mapv (fn [row] (mapv #(% a) row)) v)))
       (invoke [_ a b]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b) row)) v)))
       (invoke [_ a b cx]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx) row)) v)))
       (invoke [_ a b cx d]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d) row)) v)))
       (invoke [_ a b cx d e]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e) row)) v)))
       (invoke [_ a b cx d e f]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f) row)) v)))
       (invoke [_ a b cx d e f g]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g) row)) v)))
       (invoke [_ a b cx d e f g h]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h) row)) v)))
       (invoke [_ a b cx d e f g h i]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i) row)) v)))
       (invoke [_ a b cx d e f g h i j]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j) row)) v)))
       (invoke [_ a b cx d e f g h i j k]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k) row)) v)))
       (invoke [_ a b cx d e f g h i j k l]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l) row)) v)))
       (invoke [_ a b cx d e f g h i j k l m]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m) row)) v)))
       (invoke [_ a b cx d e f g h i j k l m n]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n) row)) v)))
       (invoke [_ a b cx d e f g h i j k l m n o]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o) row)) v)))
       (invoke [_ a b cx d e f g h i j k l m n o p]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o p) row)) v)))
       (invoke [_ a b cx d e f g h i j k l m n o p q]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o p q) row)) v)))
       (invoke [_ a b cx d e f g h i j k l m n o p q rx]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o p q rx) row)) v)))
       (invoke [_ a b cx d e f g h i j k l m n o p q rx s]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o p q rx s) row)) v)))
       (invoke [_ a b cx d e f g h i j k l m n o p q rx s t]
               (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o p q rx s t) row)) v)))
       (invoke [_ a b cx d e f g h i j k l m n o p q rx s t rest]
               (Matrix. r c (mapv (fn [row] (mapv #(apply % a b cx d e f g h i j k l m n o p q rx s t rest) row)) v)))
       (applyTo [m xs]
                (AFn/applyToHelper m xs))]

      :cljs
      [Object
       (toString [_] (pr-str v))

       IPrintWithWriter
       (-pr-writer [x writer _]
                   (write-all writer
                              "#object[sicmutils.structure.Matrix \""
                              (.toString x)
                              "\"]"))

       IEmptyableCollection
       (-empty [this] (v/zero-like this))

       ISequential

       IEquiv
       (-equiv [this that] (m:= this that))

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
       (-assoc [_ k entry] (Matrix. r c (-assoc v k entry)))
       (-contains-key? [_ k] (-contains-key? v k))

       IFind
       (-find [_ n] (-find v n))

       IFn
       (-invoke [_ a]
                (Matrix. r c (mapv (fn [row] (mapv #(% a) row)) v)))
       (-invoke [_ a b]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b) row)) v)))
       (-invoke [_ a b cx]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx) row)) v)))
       (-invoke [_ a b cx d]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d) row)) v)))
       (-invoke [_ a b cx d e]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e) row)) v)))
       (-invoke [_ a b cx d e f]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f) row)) v)))
       (-invoke [_ a b cx d e f g]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g) row)) v)))
       (-invoke [_ a b cx d e f g h]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h) row)) v)))
       (-invoke [_ a b cx d e f g h i]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i) row)) v)))
       (-invoke [_ a b cx d e f g h i j]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j) row)) v)))
       (-invoke [_ a b cx d e f g h i j k]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k) row)) v)))
       (-invoke [_ a b cx d e f g h i j k l]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l) row)) v)))
       (-invoke [_ a b cx d e f g h i j k l m]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m) row)) v)))
       (-invoke [_ a b cx d e f g h i j k l m n]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n) row)) v)))
       (-invoke [_ a b cx d e f g h i j k l m n o]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o) row)) v)))
       (-invoke [_ a b cx d e f g h i j k l m n o p]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o p) row)) v)))
       (-invoke [_ a b cx d e f g h i j k l m n o p q]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o p q) row)) v)))
       (-invoke [_ a b cx d e f g h i j k l m n o p q rx]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o p q rx) row)) v)))
       (-invoke [_ a b cx d e f g h i j k l m n o p q rx s]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o p q rx s) row)) v)))
       (-invoke [_ a b cx d e f g h i j k l m n o p q rx s t]
                (Matrix. r c (mapv (fn [row] (mapv #(% a b cx d e f g h i j k l m n o p q rx s t) row)) v)))
       (-invoke [_ a b cx d e f g h i j k l m n o p q rx s t rest]
                (Matrix. r c (mapv (fn [row] (mapv #(apply % a b cx d e f g h i j k l m n o p q rx s t rest) row)) v)))]))

(defn matrix?
  "Returns true if the supplied `m` is an instance of [[Matrix]], false
  otherwise."
  [m]
  (instance? Matrix m))

(defn- m:= [^Matrix this that]
  (and (instance? Matrix that)
       (let [^Matrix m that]
         (and (= (.-r this) (.-r m))
              (= (.-c this) (.-c m))
              (v/= (.-v this) (.-v m))))))

(defn num-rows
  "Returns the number of rows of the supplied matrix `m`. Throws if a
  non-matrix is supplied."
  [m]
  (if (matrix? m)
    (.-r ^Matrix m)
    (u/illegal (str "non-matrix supplied: " m))))

(defn num-cols
  "Returns the number of columns of the supplied matrix `m`. Throws if a
  non-matrix is supplied."
  [m]
  (if (matrix? m)
    (.-c ^Matrix m)
    (u/illegal (str "non-matrix supplied: " m))))

(defn matrix->vector
  "If `m` is already a vector, acts as identity. Else, returns the matrix as a
  vector of rows (or throws if neither of these types is passed)."
  [m]
  (cond (vector? m) m
        (matrix? m) (.-v ^Matrix m)
        :else
        (u/illegal (str "non-matrix supplied: " m))))

(defn square?
  "Returns true if `m` is a square matrix, false otherwise."
  [m]
  (and (matrix? m)
       (= (num-rows m)
          (num-cols m))))

(defn column?
  "Returns true if `m` is a matrix with a single column (a 'column matrix'),
  false otherwise."
  [m]
  (and (matrix? m)
       (= (num-cols m) 1)))

(defn row?
  "Returns true if `m` is a matrix with a single row (a 'row matrix'), false
  otherwise."
  [m]
  (and (matrix? m)
       (= (num-rows m) 1)))

(defn generate
  "Returns a matrix with `r` rows and `c` columns, whose entries are generated by
  the supplied function `f`.

  If you only supply one dimension `n` the returned matrix will be square.

  The entry in the `i`th row and `j`-th column is `(f i j)`."
  ([n f]
   (generate n n f))
  ([r c f]
   (->Matrix r c
             (mapv (fn [i]
                     (mapv (fn [j]
                             (f i j))
                           (range c)))
                   (range r)))))

(defn literal-matrix
  "Generates a `nrows` x `ncols` matrix of symbolic entries, each prefixed by
  the supplied symbol `sym`.

  NOTE: The symbols in the returned matrix record their Einstein-notation path
  into the structure that this matrix represents; a `down` of `up` columns. This
  means that the returned indices embedded in the symbols look flipped, `ji` vs
  `ij`.

  For example:

  ```clojure
  (= (literal-matrix 'x 2 2)
     (by-rows ['x_0↑0 'x_1↑0]
              ['x_0↑1 'x_1↑1]))
  ```"
  [sym nrows ncols]
  (let [prefix (str sym "_")]
    (generate nrows ncols
              (fn [i j]
                (symbol (str prefix j "↑" i))))))

(defn get-in
  "Like [[clojure.core/get-in]] for matrices, but obeying the scmutils convention:
  only one index is required to get an unboxed element from a column vector.

  NOTE that this is perhaps an unprincipled exception..."
  [m is]
  (let [e (core-get-in m is)]
    (if (and (column? m)
             (= 1 (count is)))
      (e 0)
      e)))

(defn some
  "Returns true if `f` is true for some element of the matrix `m`, false
  otherwise. (Also works on arbitrary nested sequences.)"
  [f m]
  (some f (flatten m)))

(defn fmap
  "Maps `f` over the elements of the matrix `m` returning a new matrix of the same
  dimensions as `m`."
  [f m]
  (->Matrix (num-rows m)
            (num-cols m)
            (mapv #(mapv f %) m)))

(defn fmap-indexed
  "Maps `f` over three arguments:

  - each element of the matrix `m`
  - its row `i`
  - its column `j`

  and returns a new matrix of the same dimensions as `m`. "
  [f m]
  (letfn [(process-row [i row]
            (into [] (map-indexed
                      (fn [j elem] (f elem i j))
                      row)))]
    (let [new-rows (into [] (map-indexed
                             process-row m))]
      (->Matrix (num-rows m)
                (num-cols m)
                new-rows))))

(defn- well-formed?
  "Returns true if the supplied sequence contains only sequences of the same
  length (that could be transformed into columns of a matrix), false otherwise"
  [vs]
  {:pre [(seq vs) (every? seq vs)]}
  (let [counts (map count vs)]
    (every? #(= % (first counts))
            (next counts))))

(defn by-rows*
  "Returns a matrix whose rows consist of the supplied sequence of `rows`. These
  all must be the same length.

  for a variadic equivalent, see [[by-rows]]."
  [rows]
  (if (well-formed? rows)
    (->Matrix (count rows)
              (count (first rows))
              (mapv vec rows))
    (u/illegal "malformed matrix")))

(defn by-rows
  "Returns a matrix whose rows consist of the supplied sequence of `rows`. These
  all must be the same length.

  Variadic equivalent to [[by-rows*]]."
  [& rows]
  (by-rows* rows))

(defn by-cols*
  "Returns a matrix whose columns consist of the supplied sequence of `cols`.
  These all must be the same length.

  for a variadic equivalent, see [[by-cols]]."
  [cols]
  (if (well-formed? cols)
    (->Matrix (count (first cols))
              (count cols)
              (apply mapv vector cols))
    (u/illegal "malformed matrix")))

(defn by-cols
  "Returns a matrix whose columns consist of the supplied sequence of `cols`.
  These all must be the same length.

  Variadic equivalent to [[by-cols*]]."
  [& cols]
  (by-cols* cols))

(defn row*
  "Returns a row matrix populated by the supplied `xs`. For a variadic equivalent,
  see [[row]]."
  [xs]
  {:pre [(not-empty xs)]}
  (->Matrix 1 (count xs) [(vec xs)]))

(defn row
  "Returns a row matrix populated by the supplied `xs`. Variadic equivalent
  to [[row*]]."
  [& xs]
  (row* xs))

(defn column*
  "Returns a column matrix populated by the supplied `xs`. For a variadic equivalent,
  see [[column]]."
  [xs]
  {:pre [(not-empty xs)]}
  (->Matrix (count xs) 1 (mapv vector xs)))

(defn column
  "Returns a column matrix populated by the supplied `xs`. Variadic equivalent
  to [[column*]]."
  [& xs]
  (column* xs))

(defn transpose
  "Returns the transpose of the matrix `m`. The transpose is the original matrix,
  with rows and columns swapped."
  [m]
  (generate (num-cols m)
            (num-rows m)
            #(core-get-in m [%2 %1])))

(defn ->structure
  "Returns a structure generated by converting `m` into a nested structure with
  the supplied `outer-orientation` and `inner-orientation`.

  If `t?` is true, the columns of `m` will form the inner tuples. If `t?` is
  false, the rows of `m` will form the inner tuples.

  By default, if you supply a single argument (the matrix `m`), a matrix turns
  into a single outer `::s/down` of inner columns represented as `::up`
  structures."
  ([m] (->structure m ::s/down ::s/up true))
  ([m outer-orientation inner-orientation t?]
   {:pre [(s/valid-orientation? outer-orientation)
          (s/valid-orientation? inner-orientation)]}
   (let [m'   (if t? (transpose m) m)]
     (s/make outer-orientation
             (mapv #(s/make inner-orientation %)
                   m')))))

(defn seq->
  "Convert a sequence `xs` (typically, of function arguments) to an up-structure.

  Any matrix present in the argument list will be converted to row of columns
  via [[->structure]]."
  [xs]
  (s/up* (map (fn [m]
                (if (matrix? m)
                  (->structure m)
                  m))
              xs)))

(defn- mul
  "Returns the matrix product of `a` and `b`. Throws if `a` and `b` are
  incompatible for multiplication."
  [a b]
  (let [ra (num-rows a)
        rb (num-rows b)
        ca (num-cols a)
        cb (num-cols b)]
    (when-not (= ca rb)
      (u/illegal "matrices incompatible for multiplication"))
    (generate ra cb #(reduce
                      g/+ (for [k (range ca)]
                            (g/* (core-get-in a [%1 k])
                                 (core-get-in b [k %2])))))))

(defn- elementwise
  "Applies `f` elementwise between the matrices `a` and `b`. Throws if `a` and `b`
  don't have the same dimensions."
  [f a b]
  (let [ra (num-rows a)
        rb (num-rows b)
        ca (num-cols a)
        cb (num-cols b)]
    (when (or (not= ra rb)
              (not= ca cb))
      (u/illegal "matrices incompatible for operation"))
    (generate ra ca #(f (core-get-in a [%1 %2])
                        (core-get-in b [%1 %2])))))

(defn square-structure->
  "Converts the square structure `s` into a matrix, and calls the supplied
  continuation `continue` with

  - the generated matrix
  - a function which will restore a matrix to a structure with the same inner
    and outer orientations as s

  Returns the result of the continuation call."
  [s continue]
  (let [major-size (count s)
        major-orientation (s/orientation s)
        minor-sizes (map #(if (s/structure? %) (count %) 1) s)
        minor-orientations (map s/orientation s)
        minor-orientation (first minor-orientations)]
    (if (and (every? #(= major-size %) minor-sizes)
             (every? #(= minor-orientation %) (rest minor-orientations)))
      (let [need-transpose (= minor-orientation ::s/up)
            M (generate major-size major-size
                        #(core-get-in s (if need-transpose [%2 %1] [%1 %2])))]
        (continue
         M #(->structure % major-orientation minor-orientation need-transpose)))
      (u/illegal "structure is not square"))))

(defn square-structure-operation
  "Applies matrix operation `f` to square structure `s` and returns a structure of
  the same type as the supplied structure."
  [s f]
  (square-structure-> s (fn [m ->s] (->s (f m)))))

(defn- M*u
  "Multiply a matrix by an up structure on the right. The return value is up."
  [m u]
  (when (not= (num-cols m) (count u))
    (u/illegal "matrix and tuple incompatible for multiplication"))
  (s/up*
   (map (fn [i]
          (ua/generic-sum
           (fn [k]
             (g/* (core-get-in m [i k])
                  (get u k)))
           0 (num-cols m)))
        (range (num-rows m)))))

(defn- d*M
  "Multiply a matrix `m` by down tuple `d` on the left. The return value has
  orientation `down`."
  [d m]
  (when (not= (num-rows m) (count d))
    (u/illegal "matrix and tuple incompatible for multiplication"))
  (s/down*
   (map (fn [i]
          (ua/generic-sum
           (fn [k]
             (g/* (get d k)
                  (core-get-in m [i k])))
           0 (num-rows m)))
        (range (num-cols m)))))

(def ^{:dynamic true
       :doc "Set this dynamic variable to `false` to allow [[s->m]] to operate
  on structures for which `(* ls ms rs)` does NOT yield a numerical value."}
  *careful-conversion* true)

(defn s->m
  "Convert the structure `ms`, which would be a scalar if the (compatible)
  multiplication`(* ls ms rs)` were performed, to a matrix."
  [ls ms rs]
  (when *careful-conversion*
    (assert (v/numerical? (g/* ls (g/* ms rs)))))
  (let [ndowns (s/dimension ls)
        nups   (s/dimension rs)]
    (generate ndowns nups
              (fn [i j]
                (g/* (s/unflatten
                      (s/basis-unit ndowns i) ls)
                     (g/* ms (s/unflatten
                              (s/basis-unit nups j) rs)))))))

(defn as-matrix
  "Any one argument function of a structure can be seen as a matrix. This is only
  useful if the function has a linear multiplier (e.g. derivative)"
  [F]
  (fn [s]
    (let [v (F s)]
      (s->m
       (s/compatible-shape (g/* v s)) v s))))

(defn nth-row
  "Returns the `n`-th row of the supplied matrix `m` as a `down` structure."
  [m n]
  (s/down* (get m n)))

(defn nth-col
  "Returns the `n`-th column of the supplied matrix `m` as an `up` structure."
  [m n]
  (s/up* (map #(% n) m)))

(defn diagonal
  "Returns the diagonal of the supplied matrix `m` as an up structure. Errors if a
  type other than a diagonal matrix is supplied."
  [m]
  {:pre [(square? m)]}
  (let [rows  (num-rows m)]
    (s/up* (map #(core-get-in m [% %])
                (range 0 rows)))))

(defn up->column-matrix
  "Returns a column matrix with the contents of the supplied `up` structure.
  Errors if any other type is provided."
  [v]
  {:pre [(s/up? v)]}
  (column* v))

(defn column-matrix->up
  "Returns the single column from the supplied column matrix as an `up`. Errors if
  some other type is supplied."
  [m]
  {:pre [(column? m)]}
  (nth-col m 0))

(defn column-matrix->vector
  "Returns the single column from the supplied column matrix as a vector. Errors
  if some other type is supplied."
  [m]
  {:pre [(column? m)]}
  (mapv first m))

(defn down->row-matrix
  "Returns a row matrix with the contents of the supplied `down` structure.
  Errors if any other type is provided."
  [v]
  {:pre [(s/down? v)]}
  (by-rows (s/structure->vector v)))

(defn row-matrix->down
  "Returns the single row from the supplied row matrix as a `down`. Errors if some
  other type is supplied."
  [m]
  {:pre [(row? m)]}
  (nth-row m 0))

(defn row-matrix->vector
  "Returns the single row from the supplied row matrix as a vector. Errors if some
  other type is supplied."
  [m]
  {:pre [(row? m)]}
  (nth m 0))

(defn m->s
  "Convert the matrix `m` into a structure `S`, guided by the requirement that `(*
  ls S rs)` should be a scalar."
  [ls m rs]
  (let [ncols     (num-cols m)
        col-shape (s/compatible-shape ls)
        ms (s/unflatten (for [j (range ncols)]
                          (s/unflatten (nth-col m j) col-shape))
                        (s/compatible-shape rs))]
    (when *careful-conversion*
      (assert (v/numerical? (g/* ls (g/* ms rs)))
              (str "product is not numerical: " ls ms rs)))
    ms))

(defn s:transpose [ls ms rs]
  (m->s rs (transpose (s->m ls ms rs)) ls))

(defn- delete
  "Returns the vector formed by deleting the `i`'th element of the given vector
  `v`."
  [v i]
  (vec
   (concat (take i v)
           (drop (inc i) v))))

(defn with-substituted-row
  "Returns a new matrix of identical shape to `m`, with the vector `v` substituted
  for the `i`th row."
  [m i v]
  {:pre [(matrix? m)
         (<= 0 i)
         (< i (num-rows m))
         (= (num-cols m)
            (count v))]}
  (assoc m i v))

(defn submatrix
  "Returns the submatrix of the matrix (or matrix-like structure) `s` generated by
  taking

  - rows from `lowrow` -> `hirow`,
  - columns from `lowcol` -> `hicol`"
  [x lowrow hirow lowcol hicol]
  (let [m (if (s/structure? x)
            (square-structure-> x (fn [m _] m))
            x)]
    (generate (inc (- hirow lowrow))
              (inc (- hicol lowcol))
              (fn [i j]
                (core-get-in m [(+ i lowrow)
                                (+ j lowcol)])))))

(defn without
  "Returns the matrix formed by deleting the `i`-th row and `j`-th column of the
  given matrix `m`."
  [m i j]
  (->Matrix (dec (num-rows m))
            (dec (num-cols m))
            (mapv #(delete % j)
                  (delete m i))) )

(defn- checkerboard-negate [s i j]
  (if (even? (+ i j))
    s
    (g/negate s)))

(defn dimension
  "Returns the 'dimension', ie, the number of rows & columns, of the supplied
  square matrix. Errors if some other type is supplied."
  [m]
  {:pre [(square? m)]}
  (num-rows m))

(defn trace
  "Returns the trace (the sum of diagonal elements) of the square matrix `m`.

  Generic operations are used, so this works on symbolic square matrices."
  [m]
  {:pre [(square? m)]}
  (let [rows  (num-rows m)]
    (transduce (map #(core-get-in m [% %]))
               g/+
               (range 0 rows))))

(defn determinant
  "Returns the determinant of the supplied square matrix `m`.

  Generic operations are used, so this works on symbolic square matrices."
  [m]
  {:pre [(square? m)]}
  (condp = (num-rows m)
    0 m
    1 (core-get-in m [0 0])
    2 (let [[[a b] [c d]] m]
        (g/- (g/* a d)
             (g/* b c)))
    (ua/generic-sum
     (map g/*
          (cycle [1 -1])
          (nth m 0)
          (for [i (range (num-rows m))]
            (determinant (without m 0 i)))))))

(defn cofactors
  "Returns the matrix of cofactors of the supplied square matrix `m`."
  [m]
  {:pre [(square? m)]}
  (let [r (num-rows m)]
    (cond (< r 2) m
          (= r 2) (let [[[a b] [c d]] m]
                    (->Matrix 2 2 [[d (g/negate c)]
                                   [(g/negate b) a]]))
          :else (generate r r
                          (fn [i j]
                            (-> (without m i j)
                                (determinant)
                                (checkerboard-negate i j)))))))

(defn invert
  "Returns the inverse of the supplied square matrix `m`."
  [m]
  {:pre [(square? m)]}
  (let [r (num-rows m)]
    (condp = r
      0 m
      1 (->Matrix 1 1 [[(g/invert (core-get-in m [0 0]))]])
      (let [C (cofactors m)
            Δ (ua/generic-sum (map g/* (nth m 0) (nth C 0)))]
        (fmap #(g/divide % Δ)
              (transpose C))))))

(defn s:inverse
  [ls ms rs]
  (m->s (s/compatible-shape rs)
        (invert (s->m ls ms rs))
        (s/compatible-shape ls)))

(defn make-zero
  "Return a zero-valued matrix of `m` rows and `n` columns (`nXn` if only `n` is
  supplied)."
  ([n] (make-zero n n))
  ([m n] (generate m n (constantly 0))))

(defn I
  "Return the identity matrix of order `n`."
  [n]
  (generate n n s/kronecker))

(defn identity-like
  "Return an identity matrix whose ones and zeros match the types of the supplied
  square matrix `M`. Errors if a non-square matrix `M` is supplied."
  [M]
  (if-not (square? M)
    (u/illegal "identity-like on non-square")
    (fmap-indexed (fn [elem i j]
                    (if (= i j)
                      (v/one-like elem)
                      (v/zero-like elem)))
                  M)))

(defn identity?
  "Returns true if the supplied matrix `m` is an identity matrix, false
  otherwise."
  [m]
  (and (square? m)
       (let [n (dimension m)]
         (every? true?
                 (for [i (range n)
                       j (range n)
                       :let [entry (core-get-in m [i j])]]
                   (if (= i j)
                     (v/one? entry)
                     (v/zero? entry)))))))

(defn make-diagonal
  "Returns the diagonal matrix of order `(count v)` with the elements of the
  sequence `v` along the diagonal."
  [v]
  (let [v (vec v)
        n (count v)]
    (generate n n (fn [i j]
                    (if (= i j) (v i) 0)))))

(defn diagonal?
  "Returns true if `m` is a diagonal matrix (ie, a square matrix where every
  non-diagonal element is zero), false otherwise."
  [m]
  (and (square? m)
       (let [n (dimension m)]
         (every? true?
                 (for [i (range n)
                       j (range n)
                       :when (not= i j)
                       :let [entry (core-get-in m [i j])]]
                   (v/zero? entry))))))

(defn characteristic-polynomial
  "Compute the characteristic polynomial of the square matrix m, evaluated at `x`.

  Typically `x` will be a symbolic variable, but if you wanted to get the value
  of the characteristic polynomial at some particular numerical point `x'` you
  could pass that too."
  [m x]
  (let [r (num-rows m)
        c (num-cols m)]
    (when-not (= r c) (u/illegal "not square"))
    (determinant (g/- (g/* x (I r)) m))))

;; ## Solving

(defn cramers-rule
  "Linear equations solved by Cramer's rule.

   Solves an inhomogeneous system of linear equations, `A*x=b`, where the matrix
   `A` and the column matrix `b` are given.

   returns the column matrix `X`.

   Unlike LU decomposition, Cramer's rule generalizes to symbolic solutions."
  [A b]
  {:pre [(square? A)
         (column? b)
         (= (dimension A) (num-rows b))]}
  (let [bv (nth-col b 0)
        d  (determinant A)
        At (transpose A)]
    (column*
     (mapv (fn [i]
             (g/div (determinant
                     (with-substituted-row At i bv))
                    d))
           (range bv)))))

(defn solve [A b]
  (cramers-rule A b))

(defn rsolve [b A]
  (cond (s/up? b)   (column-matrix->up
                     (solve A (up->column-matrix b)))
        (column? b) (solve A b)
        (s/down? b) (row-matrix->down
                     (transpose
                      (solve (transpose A)
                             (transpose
                              (down->row-matrix b)))))
        (row? b)
        (transpose
         (solve (transpose A)
                (transpose b)))
        :else (u/illegal (str "I don't know how to solve:" b A))))

(defmethod g/solve-linear [::square-matrix ::s/up] [A b] (rsolve b A))
(defmethod g/solve-linear [::square-matrix ::s/down] [A b] (rsolve b A))
(defmethod g/solve-linear [::square-matrix ::column-matrix] [A b] (rsolve b A))
(defmethod g/solve-linear [::square-matrix ::row-matrix] [A b] (rsolve b A))

(defmethod g/solve-linear-right [::row-matrix ::square-matrix] [b A] (rsolve b A))
(defmethod g/solve-linear-right [::down ::square-matrix] [b A] (rsolve b A))

;; ## Generic Operation Installation

(defmethod g/negate [::matrix] [a] (fmap g/negate a))
(defmethod g/sub [::matrix ::matrix] [a b] (elementwise g/- a b))
(defmethod g/add [::matrix ::matrix] [a b] (elementwise g/+ a b))
(defmethod g/mul [::matrix ::matrix] [a b] (mul a b))
(defmethod g/mul [::v/scalar ::matrix] [n a] (fmap #(g/* n %) a))
(defmethod g/mul [::matrix ::v/scalar] [a n] (fmap #(g/* % n) a))
(defmethod g/mul [::matrix ::s/up] [m u] (M*u m u))
(defmethod g/mul [::s/down ::matrix] [d m] (d*M d m))
(defmethod g/div [::s/up ::matrix] [u M] (M*u (invert M) u))
(defmethod g/exp [::square-matrix] [m] (series/exp-series m))
(defmethod g/cos [::square-matrix] [m] (series/cos-series m))
(defmethod g/sin [::square-matrix] [m] (series/sin-series m))
(defmethod g/tan [::square-matrix] [m] (series/tan-series m))
(defmethod g/sec [::square-matrix] [m] (series/sec-series m))
(defmethod g/acos [::square-matrix] [m] (series/acos-series m))
(defmethod g/asin [::square-matrix] [m] (series/asin-series m))
(defmethod g/atan [::square-matrix] [m] (series/atan-series m))
(defmethod g/cosh [::square-matrix] [m] (series/cosh-series m))
(defmethod g/sinh [::square-matrix] [m] (series/sinh-series m))
(defmethod g/tanh [::square-matrix] [m] (series/tanh-series m))
(defmethod g/asinh [::square-matrix] [m] (series/asinh-series m))
(defmethod g/atanh [::square-matrix] [m] (series/atanh-series m))
(defmethod g/simplify [::matrix] [m] (fmap g/simplify m))

(defmethod g/invert [::matrix] [m] (invert m))

(defmethod g/make-rectangular [::matrix ::matrix] [a b]
  (elementwise g/make-rectangular a b))

(defmethod g/make-polar [::matrix ::matrix] [a b]
  (elementwise g/make-polar a b))

(defmethod g/real-part [::matrix] [m] (fmap g/real-part m))
(defmethod g/imag-part [::matrix] [m] (fmap g/imag-part m))
(defmethod g/conjugate [::matrix] [m]  (fmap g/conjugate m))

(defmethod g/transpose [::matrix] [m] (transpose m))
(defmethod g/trace [::square-matrix] [m] (trace m))
(defmethod g/determinant [::square-matrix] [m] (determinant m))
(defmethod g/determinant [::s/structure] [s]
  (square-structure-> s (fn [m _] (determinant m))))

(defmethod g/trace [::s/structure] [s]
  (square-structure-> s (fn [m _] (trace m))))

(defmethod g/invert [::s/structure] [a]
  (let [a' (square-structure-operation a invert)]
    (if (= (s/orientation a') (s/orientation (first a')))
      (s/opposite a' (map #(s/opposite a' %) a'))
      a')))

(defn- s:solve-linear-left [M product]
  (let [cp (s/compatible-shape product)
        cr (s/compatible-shape (s/s:* cp M))]
    (s/s:* (s:inverse cp M cr) product)))

(defn- s:solve-linear-right [product M]
  (let [cp (s/compatible-shape product)
        cr (s/compatible-shape (s/s:* M cp))]
    (s/s:* product (s:inverse cr M cp))))

(defmethod g/div [::s/structure ::s/structure] [rv s]
  (s:solve-linear-left s rv))

(defmethod g/solve-linear [::s/structure ::s/structure] [s product]
  (s:solve-linear-left s product))

(defmethod g/solve-linear-right [::s/structure ::s/structure] [product s]
  (s:solve-linear-right product s))

(defmethod g/dimension [::square-matrix] [m] (dimension m))
(defmethod g/dimension [::column-matrix] [m] (num-rows m))
(defmethod g/dimension [::row-matrix] [m] (num-cols m))

;; ## Column / Row Matrices only...

(defmethod g/dot-product [::row-matrix ::row-matrix] [a b]
  (g/dot-product (row-matrix->down a)
                 (row-matrix->down b)))

(defmethod g/dot-product [::column-matrix ::column-matrix] [a b]
  (g/dot-product (column-matrix->up a)
                 (column-matrix->up b)))

(defmethod g/inner-product [::row-matrix ::row-matrix] [a b]
  (g/inner-product (row-matrix->vector a)
                   (row-matrix->vector b)))

(defmethod g/inner-product [::column-matrix ::column-matrix] [a b]
  (g/inner-product (column-matrix->up a)
                   (column-matrix->up b)))

(defmethod g/cross-product [::row-matrix ::row-matrix] [a b]
  (by-rows
   (s/structure->vector
    (g/cross-product (row-matrix->vector a)
                     (row-matrix->vector b)))))

(defmethod g/cross-product [::column-matrix ::column-matrix] [a b]
  (up->column-matrix
   (g/cross-product (column-matrix->up a)
                    (column-matrix->up b))))

(defmethod g/outer-product [::column-matrix ::row-matrix] [a b] (mul a b))

(defmethod g/partial-derivative [::matrix v/seqtype] [M selectors]
  (fmap #(g/partial-derivative % selectors)
        M))
