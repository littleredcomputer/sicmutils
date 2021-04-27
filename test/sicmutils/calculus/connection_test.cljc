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

(ns sicmutils.calculus.connection-test
  (:refer-clojure :exclude [+ - * /])
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [sicmutils.abstract.function :as af]
            [sicmutils.calculus.basis :as b]
            [sicmutils.calculus.coordinate :refer [let-coordinates]
             #?@(:cljs [:include-macros true])]
            [sicmutils.calculus.covariant :as cov]
            [sicmutils.calculus.connection :as conn]
            [sicmutils.calculus.curvature :as curv]
            [sicmutils.calculus.manifold :as m]
            [sicmutils.calculus.vector-field :as vf]
            [sicmutils.function :refer [compose]]
            [sicmutils.generic :as g :refer [+ - * /]]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]
            [sicmutils.structure :as s :refer [up down]]
            [sicmutils.value :as v]))

(use-fixtures :each hermetic-simplify-fixture)

(def simplify
  (comp v/freeze g/simplify))

(deftest basic-tests
  (testing "Christoffel round-trip"
    (let [Christoffel (conn/literal-Christoffel-2 'C m/R2-rect)
          rt (fn [C]
               (cov/Cartan->Christoffel
                (cov/Christoffel->Cartan C)))
          point (m/typical-point m/R2-rect)]

      (is (= ((cov/Christoffel->symbols
               (cov/symmetrize-Christoffel Christoffel))
              point)
             ((cov/Christoffel->symbols
               (cov/symmetrize-Christoffel (rt Christoffel)))
              point))
          "symmetrize-Christoffel behaves identically after round-tripping.")

      (is (= ((cov/Christoffel->symbols Christoffel) point)
             ((cov/Christoffel->symbols (rt Christoffel)) point)
             ((cov/Christoffel->symbols (rt (rt Christoffel))) point))
          "roundtripped Christoffel symbols have the same effect on a point."))))

(deftest connection-tests
  ;; These tests come from connection.scm in scmutils, 2020 edition.
  (let [two-sphere m/R2-rect]
    (let-coordinates [[theta phi] two-sphere]
      (let [g-sphere (fn [R]
                       (fn [u v]
                         (* (g/square R)
                            (+ (* (dtheta u) (dtheta v))
                               (* (compose (g/square g/sin) theta)
                                  (dphi u)
                                  (dphi v))))))]
        (is (= '(down
                 (down (down 0 0)
                       (down 0 (* (expt R 2) (sin theta0) (cos theta0))))
                 (down (down 0 (* (expt R 2) (sin theta0) (cos theta0)))
                       (down (* -1N (expt R 2) (sin theta0) (cos theta0)) 0)))
               (simplify
                ((cov/Christoffel->symbols
                  (conn/metric->Christoffel-1 (g-sphere 'R)
                                              (b/coordinate-system->basis two-sphere)))
                 ((m/point two-sphere) (up 'theta0 'phi0))))))

        (is (= '(down
                 (down (up 0 0) (up 0 (/ (cos theta0) (sin theta0))))
                 (down (up 0 (/ (cos theta0) (sin theta0)))
                       (up (* -1 (sin theta0) (cos theta0)) 0)))
               (simplify
                ((cov/Christoffel->symbols
                  (conn/metric->Christoffel-2
                   (g-sphere 'R)
                   (b/coordinate-system->basis two-sphere)))
                 ((m/point two-sphere) (up 'theta0 'phi0)))))))))

  (testing "Test with general 2d metric"
    (let-coordinates [[x y] m/R2-rect]
      (let [R2-basis (b/coordinate-system->basis R2-rect)
            fa (compose (af/literal-function 'a '(-> (UP Real Real) Real))
                        (m/chart R2-rect))
            fb (compose (af/literal-function 'b '(-> (UP Real Real) Real))
                        (m/chart R2-rect))
            fc (compose (af/literal-function 'c '(-> (UP Real Real) Real))
                        (m/chart R2-rect))
            g-R2 (fn [g_00 g_01 g_11]
                   (fn [u v]
                     (+ (* g_00 (dx u) (dx v))
                        (* g_01 (+ (* (dx u) (dy v))
                                   (* (dy u) (dx v))))
                        (* g_11 (dy u) (dy v)))))]
        (is (= '(+ (* (a (up x0 y0)) (u↑0 (up x0 y0)) (v↑0 (up x0 y0)))
                   (* (u↑0 (up x0 y0)) (b (up x0 y0)) (v↑1 (up x0 y0)))
                   (* (v↑0 (up x0 y0)) (b (up x0 y0)) (u↑1 (up x0 y0)))
                   (* (v↑1 (up x0 y0)) (u↑1 (up x0 y0)) (c (up x0 y0))))
               (simplify
                (((g-R2 fa fb fc)
                  (vf/literal-vector-field 'u R2-rect)
                  (vf/literal-vector-field 'v R2-rect))
                 ((m/point R2-rect) (up 'x0 'y0))))))
        (is (= '(down
                 (down
                  (down (* (/ 1 2) (((partial 0) a) (up x0 y0)))
                        (+ (((partial 0) b) (up x0 y0))
                           (* (/ -1 2) (((partial 1) a) (up x0 y0)))))
                  (down (* (/ 1 2) (((partial 1) a) (up x0 y0)))
                        (* (/ 1 2) (((partial 0) c) (up x0 y0)))))
                 (down
                  (down (* (/ 1 2) (((partial 1) a) (up x0 y0)))
                        (* (/ 1 2) (((partial 0) c) (up x0 y0))))
                  (down (+ (* (/ -1 2) (((partial 0) c) (up x0 y0)))
                           (((partial 1) b) (up x0 y0)))
                        (* (/ 1 2) (((partial 1) c) (up x0 y0))))))
               (simplify
                ((cov/Christoffel->symbols
                  (conn/metric->Christoffel-1 (g-R2 fa fb fc) R2-basis))
                 ((m/point R2-rect) (up 'x0 'y0)))))))))

  (testing "next block"
    (let [polar m/R2-polar]
      (let-coordinates [[r theta] polar]
        (let [polar-point ((m/point polar) (up 'r 'theta))
              polar-basis (b/coordinate-system->basis polar)
              polar-metric (fn [v1 v2]
                             (+ (* (dr v1) (dr v2))
                                (* (g/square r)
                                   (* (dtheta v1)
                                      (dtheta v2)))))]
          (is (= '(down
                   (down (up 0 0)
                         (up 0 (/ 1 r)))
                   (down (up 0 (/ 1 r))
                         (up (* -1 r) 0)))
                 (simplify
                  ((cov/Christoffel->symbols
                    (conn/metric->Christoffel-2 polar-metric polar-basis))
                   polar-point))))))))

  (testing "faster, a simplified version"
    (let [polar m/R2-rect]
      (let-coordinates [[r theta] polar]
        (let [polar-point ((m/point polar) (up 'r 'theta))
              polar-Gamma (cov/make-Christoffel
                           (let [zero m/zero-manifold-function]
                             (down
                              (down (up zero zero)
                                    (up zero (/ 1 r)))
                              (down (up zero (/ 1 r))
                                    (up (* -1 r) zero))))
                           (b/coordinate-system->basis polar))
              nabla (cov/covariant-derivative
                     (cov/Christoffel->Cartan
                      polar-Gamma))
              curvature (curv/Riemann nabla)]
          (testing "Now look at curvature:"
            (doall
             (for [alpha [dr dtheta]
                   beta [d:dr d:dtheta]
                   gamma [d:dr d:dtheta]
                   delta [d:dr d:dtheta]]
               (is (= 0 (simplify
                         ((curvature alpha beta gamma delta)
                          polar-point)))))))))))

  (testing "sphere block"
    (let [spherical m/R3-rect]
      (let-coordinates [[r theta phi] spherical]
        (let [spherical-point ((m/point spherical) (up 'r 'theta 'phi))
              spherical-basis (b/coordinate-system->basis spherical)
              spherical-metric (fn [v1 v2]
                                 (+ (* (dr v1) (dr v2))
                                    (* (g/square r)
                                       (+ (* (dtheta v1) (dtheta v2))
                                          (* (g/square (g/sin theta))
                                             (dphi v1) (dphi v2))))))]
          (is (= '(down
                   (down (up 0 0 0) (up 0 (/ 1 r) 0) (up 0 0 (/ 1 r)))
                   (down (up 0 (/ 1 r) 0) (up (* -1 r) 0 0) (up 0 0 (/ (cos theta) (sin theta))))
                   (down (up 0 0 (/ 1 r))
                         (up 0 0 (/ (cos theta) (sin theta)))
                         (up (* -1 r (expt (sin theta) 2))
                             (* -1 (cos theta) (sin theta))
                             0)))
                 (simplify
                  ((cov/Christoffel->symbols
                    (conn/metric->Christoffel-2 spherical-metric spherical-basis))
                   spherical-point))))

          ;; Thus, make simplified version.
          (let [spherical-Gamma
                (cov/make-Christoffel
                 (let [zero m/zero-manifold-function]
                   (down
                    (down (up zero zero zero)
                          (up zero (/ 1 r) zero)
                          (up zero zero (/ 1 r)))
                    (down (up zero (/ 1 r) zero)
                          (up (* -1 r) zero zero)
                          (up zero zero (/ (g/cos theta) (g/sin theta))))
                    (down (up zero zero (/ 1 r))
                          (up zero zero (/ (g/cos theta) (g/sin theta)))
                          (up (* -1 r (g/expt (g/sin theta) 2))
                              (* -1 (g/sin theta) (g/cos theta)) zero))))
                 (b/coordinate-system->basis spherical))]
            (testing"Now look at curvature:"
              (let [nabla (cov/covariant-derivative
                           (cov/Christoffel->Cartan spherical-Gamma))
                    curvature (curv/Riemann nabla)]
                (doall
                 (for [alpha [dr dtheta dphi]
                       beta [d:dr d:dtheta]
                       gamma [d:dr d:dtheta]
                       delta [d:dr d:dtheta]]
                   (is (= 0 (simplify
                             ((curvature alpha beta gamma delta)
                              spherical-point))))))))))))))

;; TODO: This one takes quite a while, so we only install this test trapped in a
;; comment.
(deftest spherical-flat-lorentz
  (comment
    (testing "MTW p205 spherical flat lorentz"
      (let [spherical-Lorentz m/R4-rect]
        (let-coordinates [[t r theta phi] spherical-Lorentz]
          (let [spherical-Lorentz-basis
                (b/coordinate-system->basis spherical-Lorentz)

                spherical-Lorentz-metric
                (fn [c-2]
                  (fn [v1 v2]
                    (+ (* -1 c-2 (* (dt v1) (dt v2)))
                       (* (dr v1) (dr v2))
                       (* (g/square r)
                          (+ (* (dtheta v1) (dtheta v2))
                             (* (g/square (g/sin theta))
                                (* (dphi v1) (dphi v2))))))))

                spherical-Lorentz-point
                ((m/point spherical-Lorentz) (up 't 'r 'theta 'phi))

                orthonormal-spherical-Lorentz-vector-basis
                (fn [c-2]
                  (down (* (/ 1 (g/sqrt c-2)) d:dt)
                        d:dr
                        (* (/ 1 r) d:dtheta)
                        (* (/ 1 (* r (g/sin theta))) d:dphi)))

                orthonormal-spherical-Lorentz-oneform-basis
                (fn [c-2]
                  (let [orthonormal-spherical-Lorentz-vectors
                        (orthonormal-spherical-Lorentz-vector-basis c-2)]
                    (b/vector-basis->dual orthonormal-spherical-Lorentz-vectors
                                          spherical-Lorentz)))

                orthonormal-spherical-Lorentz-basis
                (fn [c-2]
                  (b/make-basis
                   (orthonormal-spherical-Lorentz-vector-basis c-2)
                   (orthonormal-spherical-Lorentz-oneform-basis c-2)))

                orthonormal-spherical-Lorentz-second-connection
                (fn [c-2]
                  (let [zero m/zero-manifold-function]
                    (cov/make-Christoffel
                     (down
                      (down (up zero zero zero zero)
                            (up zero zero zero zero)
                            (up zero zero zero zero)
                            (up zero zero zero zero))
                      (down (up zero zero zero zero)
                            (up zero zero zero zero)
                            (up zero zero zero zero)
                            (up zero zero zero zero))
                      (down (up zero zero zero zero)
                            (up zero zero (/ 1 r) zero)
                            (up zero (/ -1 r) zero zero)
                            (up zero zero zero zero))
                      (down (up zero zero zero zero)
                            (up zero zero zero (/ 1 r))
                            (up zero zero zero (/ (g/cos theta)
                                                  (* r (g/sin theta))))
                            (up zero
                                (/ -1 r)
                                (/ (* -1 (g/cos theta))
                                   (* r (g/sin theta)))
                                zero)))
                     (orthonormal-spherical-Lorentz-basis c-2))))]
            (testing "look at curvature:"
              (doall
               (for [alpha [dt dr dtheta dphi]
                     beta [d:dt d:dr d:dtheta d:dphi]
                     gamma [d:dt d:dr d:dtheta d:dphi]
                     delta [d:dt d:dr d:dtheta d:dphi]]
                 (is (= 0 (simplify
                           (((curv/Riemann
                              (cov/covariant-derivative
                               (cov/Christoffel->Cartan
                                (orthonormal-spherical-Lorentz-second-connection 'c↑2))))
                             alpha beta gamma delta)
                            spherical-Lorentz-point)))))))))))))
