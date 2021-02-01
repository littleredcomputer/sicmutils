;;
;; Copyright © 2020 Sam Ritchie.
;; This work is based on the Scmutils system of MIT/GNU Scheme:
;; Copyright © 2002 Massachusetts Institute of Technology
;;
;; This is free software;  you can redistribute it and/or modify
;;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3 of the License, or (at
;; your option) any later version.
;;
;;; This software is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with this code; if not, see <http://www.gnu.org/licenses/>.
;;

(ns sicmutils.tape-test
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [same :refer [ish?]]
            [sicmutils.calculus.derivative :refer [D]]
            [sicmutils.generic :as g]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]
            [sicmutils.structure :as s]
            [sicmutils.tape :as t]
            [sicmutils.util :as u]
            [sicmutils.value :as v]))

(use-fixtures :each hermetic-simplify-fixture)

(deftest basic-tests
  (testing "simple simplification works. Everyting works only with a single
  numeric output now.

TODO handle structural outputs."
    (let [f (fn [[x y z]]
              (g/+ (g/expt x 4) (g/* x y z (g/cos x))))]
      (is (= '(down (+ (* -1 x y z (sin x))
                       (* 4 (expt x 3))
                       (* y z (cos x)))
                    (* x z (cos x))
                    (* x y (cos x)))
             (g/simplify
              ((t/gradient f) ['x 'y 'z]))))

      (is (= (g/simplify
              ((t/gradient f) ['x 'y 'z]))
             (g/simplify
              ((D f) ['x 'y 'z])))))))
