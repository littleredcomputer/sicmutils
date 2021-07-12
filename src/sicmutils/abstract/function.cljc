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

(ns sicmutils.abstract.function
  "Implementation of a [[literal-function]] constructor. Literal functions can be
  applied to structures and numeric inputs, and differentiated.

  The namespace also contains an implementation of a small language for
  declaring the input and output types of [[literal-function]] instances.

  NOTE:

   - a typed function is a function with typed metadata.
   - This MIGHT be a thing we want now, given all of the stuff from the calc
     work..."
  (:refer-clojure :exclude [name ->])
  (:require [sicmutils.abstract.number :as an]
            [sicmutils.differential :as d]
            [sicmutils.expression :as x]
            [sicmutils.function :as f]
            [sicmutils.generic :as g]
            [sicmutils.matrix :as m]
            [sicmutils.numsymb :as sym]
            [sicmutils.polynomial]
            [sicmutils.structure :as s]
            [sicmutils.util :as u]
            [sicmutils.value :as v])
  #?(:clj
     (:import (clojure.lang IFn))))

;; ## Abstract Function
;;
;; This namespace declares an abstract function type, along with the support
;; structure to process the scmutils domain/range language.

(declare literal-apply f:=)

;; This derivation allows `::function` to take advantage of all generic
;; operations installed via [[sicmutils.function]].

(derive ::function ::v/function)

;; The descriptors for literal functions look like prefix versions of the
;; standard function types. Thus, we want to be able to say:
;;
;; (literal-function 'V (-> (X Real Real) Real))
;;
;; The base types are the real numbers, designated by "Real". We will later
;; extend the system to include complex numbers, designated by "Complex".
;;
;; Types can be combined in several ways. The cartesian product of types is
;; designated by:

;; (X <type1> <type2> ...)
;;
;; We use this to specify an argument tuple of objects of the given types
;; arranged in the given order.
;;
;; Similarly, we can specify an up tuple or a down tuple with:
;;
;; (UP <type1> <type2> ...)
;; (DOWN <type1> <type2> ...)
;;
;; We can also specify a uniform tuple of a number of elements of the
;; same type using:
;;
;; (UP* <type> [n])
;; (DOWN* <type> [n])
;;
;; To get started... Type expressions are self-evaluating.

(def Real 'Real)

(defn X
  ([] (u/illegal "Null type argument -- X"))
  ([t] t)
  ([t & ts] (apply list 'X t ts)))

(defn UP
  ([] (u/illegal "Null type argument -- UP"))
  ([t] t)
  ([t & ts] (apply list 'UP t ts)))

(defn DOWN
  ([] (u/illegal "Null type argument -- DOWN"))
  ([t] t)
  ([t & ts] (apply list 'DOWN t ts)))

(defn EXPT [t n]
  (apply X (repeat n t)))

;; Examples:
;; (UP* Real 2 (UP Real Real) 2)
;; => (UP Real Real (UP Real Real) (UP Real Real))
;;
;; (UP* Real 2 (UP Real Real) 2 Real)
;; => (UP* Real Real (UP Real Real) (UP Real Real) Real)

(defn- starify [xs starred-sym unstarred-fn]
  (if (empty? xs)
    (u/illegal (str "Null type argument -- " starred-sym))
	  (loop [xs xs
           current nil
           explicit? false
           types []]
	    (if (empty? xs)
        (if explicit?
          (apply unstarred-fn types)
          (cons starred-sym types))
        (let [[x & more] xs]
		      (if (integer? x)
		        (if current
		          (recur more
                     false
                     true
			               (into types (repeat (dec x) current)))
		          (u/illegal "Bad type arguments" starred-sym xs))
            (recur more x false (conj types x))))))))

(defn X* [& rest]
  (starify rest 'X* X))

(defn UP* [& rest]
  (starify rest 'UP* UP))

(defn DOWN* [& rest]
  (starify rest 'DOWN* DOWN))

(defn -> [domain range]
  (list '-> domain range))

(def Any 'Any)

(defn default-type [n]
  (if (= n 1)
    (-> Real Real)
    (-> (X* Real n) Real)))

(defn permissive-type [n]
  (-> (X* Any n) Real))

;; Some useful types

(defn Lagrangian
  "n = #degrees-of-freedom"
  ([] (-> (UP* Real (UP* Real) (UP* Real)) Real))
  ([n] (-> (UP Real (UP* Real n) (UP* Real n)) Real)))

(defn Hamiltonian
  "n = #degrees-of-freedom"
  ([] (-> (UP Real (UP* Real) (DOWN* Real)) Real))
  ([n] (-> (UP Real (UP* Real n) (DOWN* Real n)) Real)))

(defn process-type
  "combo of all type-> functions."
  [t]
  {:pre [(sequential? t)]}
  (let [[arrow domain range] t]
    (if-not (and (= '-> arrow) domain range)
      (u/illegal
       (str "A SICM signature is of the form '(-> domain range), got: "
            arrow domain range))
      (let [[dtypes arity]
            (cond (and (sequential? domain)
                       (= (first domain) 'X))
                  (let [types (into [] (rest domain))]
                    [types [:exactly (count types)]])

                  (and (sequential? domain)
                       (= (first domain) 'X*))
                  [[domain] [:at-least 0]]

                  :else [domain [:exactly 1]])]
        {:domain domain
         :range-type range
         :domain-types dtypes
         :arity arity}))))

;; Existing Stuff. There is a BIT more in `litfun.scm` that we should read to
;; figure out what is going on.

(defn- sicm-set->exemplar
  "Convert a SICM-style set (e.g., Real or (UP Real Real)) to
  an exemplar (an instance of the relevant type)."
  [s]
  (cond (= s 'Real) 0
        (sequential? s)
        (let [[ctor & [type arity :as args]] s]
          (case ctor
            X     (mapv sicm-set->exemplar args)
            UP    (s/up* (map sicm-set->exemplar args))
            DOWN  (s/down* (map sicm-set->exemplar args))
            X*    (into [] (repeat arity (sicm-set->exemplar type)))
            UP*   (s/up* (repeat arity (sicm-set->exemplar type)))
            DOWN* (s/down* (repeat arity (sicm-set->exemplar type)))))
        :else
        (u/illegal "error!")))

;; TODO SHOULD NOT handle an "X" type in the range.

(defn sicm-signature->domain-range
  "Convert a SICM-style literal function signature,
  e.g., '(-> Real (X Real Real))
  to our 'exemplar' format."
  [[arrow domain range]]
  (when-not (and (= '-> arrow) domain range)
    (u/illegal
     (str "A SICM signature is of the form '(-> domain range), got: "
          arrow domain range)))
  (let [d (sicm-set->exemplar domain)
        d (if (vector? d) d [d])
        r (sicm-set->exemplar range)]
    [d r]))

;; TODO add metadata!! How did we get away with not having this yet?
;; TODO trawl for other uses of the star constructors, replace those around the library.

(deftype Function [name arity domain range]
  v/Value
  (zero? [_] false)
  (one? [_] false)
  (identity? [_] false)
  (zero-like [_] (fn [& _] (v/zero-like range)))
  (one-like [_] (fn [& _] (v/one-like range)))
  (identity-like [_]
    (let [meta {:arity arity :from ::v/identity-like}]
      (with-meta identity meta)))
  (exact? [f] (f/compose v/exact? f))
  (freeze [_] (v/freeze name))
  (kind [_] ::function)

  f/IArity
  (arity [_] arity)

  Object
  (toString [_] (str name))
  #?(:clj (equals [a b] (f:= a b)))

  #?@(:clj
      [IFn
       (invoke [this x] (literal-apply this [x]))
       (invoke [this x y] (literal-apply this [x y]))
       (invoke [this x y z] (literal-apply this [x y z]))
       (invoke [this w x y z] (literal-apply this [w x y z]))
       (applyTo [this xs] (literal-apply this xs))]

      :cljs
      [IEquiv
       (-equiv [a b] (f:= a b))

       IPrintWithWriter
       (-pr-writer [x writer _]
                   (write-all writer (.toString x)))

       IFn
       (-invoke [this a]
                (literal-apply this [a]))
       (-invoke [this a b]
                (literal-apply this [a b]))
       (-invoke [this a b c]
                (literal-apply this [a b c]))
       (-invoke [this a b c d]
                (literal-apply this [a b c d]))
       (-invoke [this a b c d e]
                (literal-apply this [a b c d e]))
       (-invoke [this a b c d e f]
                (literal-apply this [a b c d e f]))
       (-invoke [this a b c d e f g]
                (literal-apply this [a b c d e f g]))
       (-invoke [this a b c d e f g h]
                (literal-apply this [a b c d e f g h]))
       (-invoke [this a b c d e f g h i]
                (literal-apply this [a b c d e f g h i]))
       (-invoke [this a b c d e f g h i j]
                (literal-apply this [a b c d e f g h i j]))
       (-invoke [this a b c d e f g h i j k]
                (literal-apply this [a b c d e f g h i j k]))
       (-invoke [this a b c d e f g h i j k l]
                (literal-apply this [a b c d e f g h i j k l]))
       (-invoke [this a b c d e f g h i j k l m]
                (literal-apply this [a b c d e f g h i j k l m]))
       (-invoke [this a b c d e f g h i j k l m n]
                (literal-apply this [a b c d e f g h i j k l m n]))
       (-invoke [this a b c d e f g h i j k l m n o]
                (literal-apply this [a b c d e f g h i j k l m n o]))
       (-invoke [this a b c d e f g h i j k l m n o p]
                (literal-apply this [a b c d e f g h i j k l m n o p]))
       (-invoke [this a b c d e f g h i j k l m n o p q]
                (literal-apply this [a b c d e f g h i j k l m n o p q]))
       (-invoke [this a b c d e f g h i j k l m n o p q r]
                (literal-apply this [a b c d e f g h i j k l m n o p q r]))
       (-invoke [this a b c d e f g h i j k l m n o p q r s]
                (literal-apply this [a b c d e f g h i j k l m n o p q r s]))
       (-invoke [this a b c d e f g h i j k l m n o p q r s t]
                (literal-apply this [a b c d e f g h i j k l m n o p q r s t]))
       (-invoke [this a b c d e f g h i j k l m n o p q r s t rest]
                (literal-apply this (concat [a b c d e f g h i j k l m n o p q r s t]  rest)))]))

#?(:clj
   (defmethod print-method Function [^Function f ^java.io.Writer w]
     (.write w (.toString f))))

(defn literal-function?
  "Returns true if the supplied object is an instance of [[Function]], false
  otherwise."
  [f]
  (instance? Function f))

(defn- name
  "Returns the `-name` field of the supplied [[Function]] object. Errors if any
  other type is supplied."
  [f]
  {:pre [(literal-function? f)]}
  (.-name ^Function f))

(defn- domain-types
  "Returns the `-domain` field of the supplied [[Function]] object. Errors if any
  other type is supplied."
  [f]
  {:pre [(literal-function? f)]}
  (.-domain ^Function f))

(defn- range-type
  "Returns the `-range` field of the supplied [[Function]] object. Errors if any
  other type is supplied."
  [f]
  {:pre [(literal-function? f)]}
  (.-range ^Function f))

(defn- f:=
  "Returns true if the function `a` equals `b`, false otherwise."
  [a b]
  (and (literal-function? b)
       (= (name a) (name b))
       (= (domain-types a) (domain-types b))
       (= (range-type a) (range-type b))))

;; TODO allow for functions in range!
;;
;; (((literal-function 'f (-> Real (-> Real Real))) 'x) 'y)
;; ((f x) y)

(defn literal-function
  ([f] (->Function f [:exactly 1] [0] 0))
  ([f signature]
   (let [[domain range] (sicm-signature->domain-range signature)]
     (literal-function f domain range)))
  ([f domain range]
   (cond (number? range)
         (let [arity (if (vector? domain)
                       (count domain)
                       1)]
           (->Function f [:exactly arity]
                       (if (vector? domain) domain [domain])
                       range))

         (s/structure? range)
         (let [n           (count range)
               orientation (s/orientation range)
               template    (s/literal f n orientation)]

           (s/mapr #(literal-function %1 domain %2)
                   template
                   range))
         :else
         (u/illegal (str "WTF range" range)))))

(defn ^:no-doc binding-pairs [litfns]
  (letfn [(extract-sym [entry]
            (if (symbol? entry) entry (first entry)))
          (entry->fn [entry]
            (cond (symbol? entry) `(literal-function (quote ~entry))

                  (and (sequential? entry) (= (count entry) 3))
                  (let [[sym domain range] entry]
                    `(literal-function (quote ~sym) ~domain ~range))

                  :else (u/illegal (str "unknown literal function type" entry))))]
    (mapv (fn [entry]
            [(extract-sym entry)
             (entry->fn entry)])
          litfns)))

(defmacro with-literal-functions [litfns & body]
  (let [pairs    (binding-pairs litfns)
        bindings (into [] cat pairs)]
    `(let ~bindings ~@body)))

;; ## Differentiation of literal functions

(defn- literal-partial [f path]
  (let [fexp (if (= (f/arity f) [:exactly 1]) ;; univariate
               (if (= (first path) 0)
                 (if (= (count path) 1)
                   ;; Special-case the single argument case, or a unary function
                   ;; that's provided with a structure of a single entry.
                   (sym/derivative (name f))
                   `((~'partial ~@(next path)) ~(name f)))
                 (u/illegal "wrong indices"))
               ;; If the function takes multiple arguments we DO need to index
               ;; into that first layer. (else the first layer is added.)
               `((~'partial ~@path) ~(name f)))]
    (->Function
     fexp (f/arity f) (domain-types f) (range-type f))))

(defn- literal-derivative
  "Takes a literal function `f` and a sequence of arguments `xs`, and generates an
  expanded `((D f) xs)` by applying the chain rule and summing the partial
  derivatives for each differential argument in the input structure."
  [f xs]
  (let [v        (m/seq-> xs)
        flat-v   (flatten v)
        tag      (apply d/max-order-tag flat-v)
        ve       (s/mapr #(d/primal-part % tag) v)
        partials (s/map-chain
                  (fn [x path _]
                    (let [dx (d/tangent-part x tag)]
                      (if (v/zero? dx)
                        0
                        (d/d:* (literal-apply
                                (literal-partial f path) ve)
                               dx))))
                  v)]
    (apply d/d:+ (apply f ve) (flatten partials))))

(defn- check-argument-type
  "Check that the argument provided at index i has the same type as the exemplar
  expected."
  [f provided expected indexes]
  (cond (number? expected)
        (when-not (v/numerical? provided)
          (u/illegal (str "expected numerical quantity in argument " indexes
                          " of function call " f
                          " but got " provided)))
        (s/structure? expected)
        (do (when-not (and (or (s/structure? provided) (sequential? provided))
                           (= (s/orientation provided) (s/orientation expected))
                           (= (count provided) (count expected)))
              (u/illegal (str "expected structure matching " expected
                              " but got " provided )))
            (doseq [[provided expected sub-index] (map list provided expected (range))]
              (check-argument-type f provided expected (conj indexes sub-index))))
        (keyword? expected) ;; a keyword has to match the argument's kind
        (when-not (= (v/kind provided) expected)
          (u/illegal (str "expected argument of type " expected " but got " (v/kind provided)
                          " in call to function " f)))

        :else (u/illegal (str "unexpected argument example. got " provided " want " expected))))

(defn- literal-apply [f xs]
  (check-argument-type f xs (domain-types f) [0])
  (if (some d/perturbed? xs)
    (literal-derivative f xs)
    (an/literal-number `(~(name f) ~@(map v/freeze xs)))))
