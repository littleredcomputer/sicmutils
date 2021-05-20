;;
;; Copyright © 2021 Sam Ritchie.
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

(ns sicmutils.polynomial.exponent-test
  (:require [clojure.test :refer [is deftest testing]]
            [sicmutils.polynomial.exponent :as xpt]))

(deftest monomial-ordering-tests
  (testing "monomial orderings"
    (let [x3 (xpt/dense->exponents [3 0 0])
          x2z2 (xpt/dense->exponents [2 0 2])
          xy2z (xpt/dense->exponents [1 2 1])
          z2   (xpt/dense->exponents [0 0 2])
          monomials [x3 x2z2 xy2z z2]
          sort-with #(sort % monomials)]
      (is (= [z2 xy2z x2z2 x3]
             (sort-with xpt/lex-order)))

      (is (= [z2 x3 x2z2 xy2z]
             (sort-with xpt/graded-reverse-lex-order)))

      (is (= [z2 x3 xy2z x2z2]
             (sort-with xpt/graded-lex-order))))))