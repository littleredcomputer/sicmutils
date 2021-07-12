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

(ns sicmutils.util.vector-set-test
  (:require [clojure.set :as cs]
            [clojure.test :refer [is deftest testing]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]
             #?@(:cljs [:include-macros true])]
            [sicmutils.generators :as sg]
            [sicmutils.util.vector-set :as vs]))

(deftest vector-set-tests
  (testing "union"
    (checking "vs/union is correct" 100
              [v1 (sg/vector-set gen/nat)
               v2 (sg/vector-set gen/nat)]
              (is (= (vs/make (into v1 v2))
                     (vs/union v1 v2))
                  "optimized way matches cheap way")

              (is (= (sort (cs/union (set v1) (set v2)))
                     (vs/union v1 v2))
                  "vs/union matches clojure.set/union"))

    (checking "union(x, []) == union([], x) == x" 100
              [v (sg/vector-set gen/nat)]
              (is (= v (vs/union vs/empty-set v)))
              (is (= v (vs/union v vs/empty-set)))))

  (testing "intersection"
    (checking "vs/intersection is correct" 100
              [v1 (sg/vector-set gen/nat)
               v2 (sg/vector-set gen/nat)]
              (is (= (sort (cs/intersection (set v1) (set v2)))
                     (vs/intersection v1 v2))
                  "vs/intersection matches clojure.set/intersection"))

    (checking "intersection(v, []) == intersection([], v) == []" 100
              [v (sg/vector-set gen/nat)]
              (is (= vs/empty-set (vs/intersection vs/empty-set v)))
              (is (= vs/empty-set (vs/intersection v vs/empty-set)))))

  (testing "difference"
    (checking "vs/difference is correct" 100
              [v1 (sg/vector-set gen/nat)
               v2 (sg/vector-set gen/nat)]
              (is (= (sort (cs/difference (set v1) (set v2)))
                     (vs/difference v1 v2))
                  "vs/difference matches clojure.set/difference"))

    (checking "difference([], x) == []" 100 [v (sg/vector-set gen/nat)]
              (is vs/empty-set (vs/difference vs/empty-set v)))

    (checking "difference(x, []) == x" 100 [v (sg/vector-set gen/nat)]
              (is (= v (vs/difference v vs/empty-set)))))

  (testing "symmetric difference"
    (checking "algebraic properties" 100
              [v1 (sg/vector-set gen/nat)
               v2 (sg/vector-set gen/nat)
               v3 (sg/vector-set gen/nat)]
              (is (= v1
                     (vs/symmetric-difference v1 [])
                     (vs/symmetric-difference [] v1))
                  "empty set is neutral")

              (is (= [] (vs/symmetric-difference v1 v1))
                  "every set is its own inverse")

              (is (= (vs/symmetric-difference v1 v2)
                     (vs/symmetric-difference v2 v1))
                  "symmetric difference is commutative")

              (is (= (vs/symmetric-difference
                      v1
                      (vs/symmetric-difference v2 v3))
                     (vs/symmetric-difference
                      (vs/symmetric-difference v1 v2)
                      v3))
                  "symmetric difference is associative"))

    (checking "vs/symmetric-difference is correct" 100
              [v1 (sg/vector-set gen/nat)
               v2 (sg/vector-set gen/nat)]
              (let [s1      (set v1)
                    s2      (set v2)
                    symdiff (vs/symmetric-difference v1 v2)]
                (is (= (sort
                        (cs/union
                         (cs/difference s1 s2)
                         (cs/difference s2 s1)))
                       symdiff)
                    "symmetric diff == union of relative complements")

                (is (= (sort (cs/difference
                              (cs/union s1 s2)
                              (cs/intersection s1 s2)))
                       symdiff)
                    "vs/symmetric-difference matches (union s1 s2) - (intersection s1 s2)")))
    )

  (testing "conj"
    (checking "conj every element matches make" 100
              [v (gen/vector gen/nat)]
              (is (= (vs/make v)
                     (reduce vs/conj
                             vs/empty-set
                             (distinct v)))
                  "adding every element matches the original."))

    (testing "conj for existing element throws"
      (is (thrown? #?(:clj IllegalArgumentException :cljs js/Error)
                   (vs/conj [1 2 3] 1))))

    (checking "conj(v, x) == union(v, [x]) == union([x])" 100
              [v (sg/vector-set gen/nat)]
              (doseq [x v]
                (is (= v (vs/conj (vs/disj v x) x)))
                (is (= v (vs/union [x] (vs/disj v x))))
                (is (= v (vs/union (vs/disj v x) [x]))))))

  (testing "disj"
    (checking "disj every element == empty" 100
              [v (sg/vector-set gen/nat)]
              (is (= vs/empty-set
                     (reduce vs/disj v (shuffle v)))
                  "removing every element from the original (in any order)
                eventually gets to empty."))

    (checking "disj(v, x) == difference(v, [x])" 100
              [v (sg/vector-set gen/nat)]
              (doseq [x v]
                (is (= (vs/disj v x)
                       (vs/difference v [x]))))))

  (checking "disj/conj acts as identity" 100
            [v (sg/vector-set gen/nat)
             x gen/nat]
            (if (vs/contains? v x)
              (is (= v (-> (vs/disj v x)
                           (vs/conj x))))
              (is (= v (-> (vs/conj v x)
                           (vs/disj x))))))

  (checking "disj, conj maintain sort" 100
            [v (sg/vector-set gen/nat)]
            (let [x   (inc (apply max (or (seq v) [0])))
                  v+x (vs/conj v x)
                  v'  (vs/disj v+x x)]
              (is (not (vs/contains? v x))
                  "check that the element greater than the max isn't present")

              (is (vs/contains? v+x x))

              (is (= v+x (sort v+x))
                  "conj maintains sort")

              (is (= v' (sort v'))
                  "disj maintains sort")

              (is (= (count v+x)
                     (inc (count v)))
                  "conj increases length")

              (is (= x (peek v+x))
                  "vector-set is a vector and new max elements go on the end."))))
