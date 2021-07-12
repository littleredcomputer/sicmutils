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

(ns sicmutils.util.permute
  "Utilities for generating permutations of sequences."
  #?(:clj
     (:import (clojure.lang APersistentVector))))

(defn- delete-nth
  "returns the sequence `xs` with its `n`th element dropped."
  [xs n]
  (concat (take n xs)
          (drop (inc n) xs)))

(defn permutations
  "Returns a lazy sequence of every possible arrangement of the elements of `xs`."
  [xs]
  (if (empty? xs)
    '(())
    (letfn [(f [i item]
              (map (fn [perm]
                     (cons item perm))
                   (permutations
                    (delete-nth xs i))))]
      (sequence (comp (map-indexed f) cat)
                xs))))

(defn combinations
  "Returns a lazy sequence of every possible set of `p` elements chosen from
  `xs`."
  [xs p]
  (cond (zero? p) '(())
        (empty? xs) ()
        :else (concat
               (map (fn [more]
                      (conj more (first xs)))
                    (combinations (rest xs)
                                  (dec p)))
               (combinations (rest xs) p))))

(defn cartesian-product
  "Accepts a sequence of collections `colls` and returns a lazy sequence of the
  cartesian product of all collections.

  The cartesian product of N collections is a sequences of sequences, each `N`
  long, of every possible way of choosing `N` items where the first comes from
  the first entry in `colls`, the second from the second entry and so on.

  NOTE: This implementation comes from Alan Malloy at this [StackOverflow
  post](https://stackoverflow.com/a/18248031). Thanks, Alan!"
  [colls]
  (if (empty? colls)
    '(())
    (for [more (cartesian-product (rest colls))
          x (first colls)]
      (cons x more))))

(defn list-interchanges
  "Given a `permuted-list` and the `original-list`, returns the number of
  interchanges required to generate the permuted list from the original list."
  [permuted-list original-list]
  (letfn [(lp1 [plist n]
            (if (empty? plist)
              n
              (let [fp     (first plist)
                    bigger (rest (drop-while #(not= % fp) original-list))
                    more   (rest plist)]
                (lp2 n bigger more more 0))))
          (lp2 [n bigger more l increment]
            (if (empty? l)
              (lp1 more (+ n increment))
              (lp2 n bigger more
                   (rest l)
                   (if-not (some #{(first l)} bigger)
                     (inc increment)
                     increment))))]
    (lp1 permuted-list 0)))

(defn permutation-interchanges [permuted-list]
  (letfn [(lp1 [plist n]
            (if (empty? plist)
              n
              (let [[x & xs] plist]
                (lp2 n x xs xs 0))))
          (lp2 [n x xs l increment]
            (if (empty? l)
              (lp1 xs (+ n increment))
              (lp2 n x xs
                   (rest l)
                   (if (>= (first l) x)
                     increment
                     (inc increment)))))]
    (lp1 permuted-list 0)))

(defn- same-set?
  "Returns true if `x1` and `x2` contain the same elements, false otherwise."
  [x1 x2]
  (= (sort-by hash x1)
     (sort-by hash x2)))

(defn permutation-parity
  "If a single `permuted-list` is supplied, returns the parity of the number of
  interchanges required to sort the permutation.

  For two arguments, given a `permuted-list` and the `original-list`, returns
  the parity (1 for even, -1 for odd) of the number of the number of
  interchanges required to generate the permuted list from the original list.

  In the two-argument case, if the two lists aren't permutations of each other,
  returns 0."
  ([permuted-list]
   (let [swaps (permutation-interchanges permuted-list)]
     (if (even? swaps) 1 -1)))
  ([permuted-list original-list]
   (if (and (= (count permuted-list)
               (count original-list))
            (same-set? permuted-list original-list))
     (if (even? (list-interchanges permuted-list original-list))
       1
       -1)
     0)))

(defn permute
  "Given a `permutation` (represented as a list of numbers), and a sequence `xs`
  to be permuted, construct the list so permuted."
  [permutation xs]
  (let [xs (vec xs)]
    (map (fn [p] (get xs p))
         permutation)))

(defn- index-of [v x]
  #?(:clj (.indexOf ^APersistentVector v x)
     :cljs (-indexOf v x)))

(defn sort-and-permute
  "cont = (fn [ulist slist perm iperm] ...)

  Given a short list and a comparison function, to sort the list by the
  comparison, returning the original list, the sorted list, the permutation
  procedure and the inverse permutation procedure developed by the sort."
  [ulist <? cont]
  (let [n       (count ulist)
        lsource (map vector ulist (range n))
        ltarget (sort-by first (comparator <?) lsource)
        sorted  (mapv first ltarget)
        perm    (mapv second ltarget)
        iperm   (map (fn [i] (index-of perm i))
                     (range n))]
    (cont ulist
          sorted
          (fn [l] (permute perm l))
          (fn [l] (permute iperm l)))))

;; Sometimes we want to permute some of the elements of a list, as follows:

(defn subpermute
  "Given a sequence `xs` and a map `m` of replacement indices, returns a new
  version of `xs` with the element at the position marked by each key in `m`
  replaced by the element at each value in the original `xs`."
  [m xs]
  (reduce-kv (fn [acc k v]
               (assoc acc k (get xs v)))
             xs
             m))

(defn factorial
  "Returns the factorial of `n`, ie, the product of 1 to `n` (inclusive)."
  [n]
  (apply * (range 1 (inc n))))

(defn number-of-permutations
  "Returns the number of possible ways of permuting a collection of `n` distinct
  elements."
  [n]
  (factorial n))

(defn number-of-combinations
  "Returns the number of possible ways of choosing `k` distinct elements from a
  collection of `n` total items."
  [n k]
  (quot (factorial n)
        (* (factorial (- n k))
           (factorial k))))

(defn permutation-sequence
  "Produces an iterable sequence developing the permutations of the input sequence
  of objects (which are considered distinct) in church-bell-changes order - that
  is, each permutation differs from the previous by a transposition of adjacent
  elements (Algorithm P from §7.2.1.2 of Knuth).

  This is an unusual way to go about this in a functional language, but it's
  fun.

  This approach has the side-effect of arranging for the parity of the generated
  permutations to alternate; the first permutation yielded is the identity
  permutation (which of course is even).

  Inside, there is a great deal of mutable state, but this cannot be observed by
  the user."
  [as]
  (let [n (count as)
        a (object-array as)
        c (int-array n (repeat 0)) ;; P1. [Initialize.]
        o (int-array n (repeat 1))
        return #(into [] %)
        the-next (atom (return a))
        has-next (atom true)
        ;; step implements one-through of algorithm P up to step P2,
        ;; at which point we return false if we have terminated, true
        ;; if a has been set to a new permutation. Knuth's code is
        ;; one-based; this is zero-based.
        step (fn [j s]
               (let [q (int (+ (aget c j) (aget o j)))] ;; P4. [Ready to change?]
                 (cond (< q 0)
                       (do ;; P7. [Switch direction.]
                         (aset o j (int (- (aget o j))))
                         (recur (dec j) s))

                       (= q (inc j))
                       (if (zero? j)
                         false ;; All permutations have been delivered.
                         (do (aset o j (int (- (aget o j)))) ;; P6. [Increase s.]
                             (recur (dec j) (inc s)))) ;; P7. [Switch direction.]

                       :else ;; P5. [Change.]
                       (let [i1 (+ s (- j (aget c j)))
                             i2 (+ s (- j q))
                             t (aget a i1)
                             ]
                         (aset a i1 (aget a i2))
                         (aset a i2 t)
                         (aset c j q)
                         true ;; More permutations are forthcoming.
                         ))))]
    (#?(:clj iterator-seq :cljs #'cljs.core/chunkIteratorSeq)
     (reify #?(:clj java.util.Iterator :cljs Object)
       (hasNext [_] @has-next)
       (next [_]  ;; P2. [Visit.]
         (let [prev @the-next]
           (reset! has-next (step (dec n) 0))
           (reset! the-next (return a))
           prev))

       #?@(:cljs
           [IIterable
            (-iterator [this] this)])))))
