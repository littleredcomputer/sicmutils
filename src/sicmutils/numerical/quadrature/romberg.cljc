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

(ns sicmutils.numerical.quadrature.romberg
  (:require [sicmutils.numerical.quadrature.common :as qc]
            [sicmutils.numerical.quadrature.midpoint :as qm]
            [sicmutils.numerical.quadrature.trapezoid :as qt]
            [sicmutils.numerical.interpolate.richardson :as ir]
            [sicmutils.util.stream :as us]))

;; ## Romberg's Method
;;
;; Romberg's method is a technique for estimating a definite integral over a
;; closed (or open) range $a, b$:
;;
;; $$\int_{a}^{b} f(x) dx$$
;;
;; By applying Richardson extrapolation (see `richardson.cljc`) to either the
;; Trapezoid method or the Midpoint method.
;;
;; The implementation of Richardson extrapolation in this library can be applied
;; to any methods; many of the numerical quadrature methods (Simpson, Simpson's
;; 3/8, Milne, Boole) involve a single step of Richardson extrapolation.
;;
;; Romberg integration goes all the way. A nice way to think about this
;; algorithm is this:
;;
;; - Generate a sequence of estimates of the definite integral using the
;;   Trapezoid or Midpoint methods on a geometrically increasing number of
;;   integration slices of width $h$. This gives you a sequence of $N$ points of
;;   the form $(h, A(h))$, where $A$ is the integral estimate.
;;
;; - Each time a new point becomes available, fit a polynomial of order $N-1$ to
;;   all $N$ points... and then extrapolate to $A(0)$, the magical area estimate
;;   where the width of each integration slice is 0.
;;
;; For a wonderful reference that builds up to the ideas of Richardson
;; extrapolation and Romberg integration, see Sussman's ["Abstraction in
;; Numerical
;; Methods"](https://dspace.mit.edu/bitstream/handle/1721.1/6060/AIM-997.pdf?sequence=2)
;;
;; References:
;;
;; - Press's Numerical Recipes (p134), Section 4.3 http://phys.uri.edu/nigh/NumRec/bookfpdf/f4-3.pdf
;; - Numerical Recipes 4.4 for open-interval Romberg http://phys.uri.edu/nigh/NumRec/bookfpdf/f4-4.pdf
;; - Halfant & Sussman, ["Abstraction in Numerical
;;   Methods"](https://dspace.mit.edu/bitstream/handle/1721.1/6060/AIM-997.pdf?sequence=2).
;; - Wikipedia: https://en.wikipedia.org/wiki/Romberg%27s_method

(defn open-sequence
  "Returns a (lazy) sequence of successively refined estimates of the integral of
  `f` over the open interval $(a, b)$ by applying Richardson extrapolation to
  successive integral estimates from the Midpoint rule.

  Returns estimates formed by combining $n, 3n, 9n, ...$ slices, geometrically
  increasing by a factor of 3 with each estimate. This factor of 3 is because,
  internally, the Midpoint method is able to recycle old function evaluations
  through this factor of 3.

  Romberg integration converges quite fast by cancelling one error term in the
  taylor series expansion of $f$ with each examined term. If your function is
  /not/ smooth this may cause you trouble, and you may want to investigate a
  lower-order method.

  If supplied, `n` (defaults 1) specifies the initial number of slices to use."
  ([f a b] (open-sequence f a b {:n 1}))
  ([f a b {:keys [n] :or {n 1} :as opts}]
   {:pre [(number? n)]}
   (-> (qm/midpoint-sequence f a b opts)
       (ir/richardson-sequence 3 2 2))))

(defn closed-sequence
  "Returns a (lazy) sequence of successively refined estimates of the integral of
  `f` over the closed interval $[a, b]$ by applying Richardson extrapolation to
  successive integral estimates from the Trapezoid rule.

  Returns estimates formed by combining $n, 2n, 4n, ...$ slices, geometrically
  increasing by a factor of 2 with each estimate.

  Romberg integration converges quite fast by cancelling one error term in the
  taylor series expansion of $f$ with each examined term. If your function is
  /not/ smooth this may cause you trouble, and you may want to investigate a
  lower-order method.

  If supplied, `n` (defaults 1) specifies the initial number of slices to use."
  ([f a b] (closed-sequence f a b {:n 1}))
  ([f a b {:keys [n] :as opts}]
   {:pre [(number? n)]}
   (-> (qt/trapezoid-sequence f a b opts)
       (ir/richardson-sequence 2 2 2))))

(defn romberg-sequence
  "TODO add docs for this."
  ([f a b] (romberg-sequence f a b {}))
  ([f a b opts]
   (let [seq-fn (if (qc/closed?
                     (qc/interval opts))
                  closed-sequence
                  open-sequence)]
     (seq-fn f a b opts))))

(def ^{:doc "Returns an estimate of the integral of `f` over the open interval $(a, b)$
  generated by applying Richardson extrapolation to successive integral
  estimates from the Midpoint rule.

  Considers $1, 3, 9 ... 3^n$ windows into $(a, b)$ for each successive
  estimate.

  Optionally accepts `opts`, a dict of optional arguments. All of these get
  passed on to `us/seq-limit` to configure convergence checking.

  See `romberg-sequence` for more information about Romberg integration, and
  caveats that might apply when using this integration method."}
  open-integral
  (qc/make-integrator-fn
   qm/single-midpoint
   open-sequence))

(def ^{:doc
       "Returns an estimate of the integral of `f` over the closed interval $[a, b]$
  generated by applying Richardson extrapolation to successive integral
  estimates from the Trapezoid rule.

  Considers $1, 2, 4 ... 2^n$ windows into $[a, b]$ for each successive
  estimate.

  Optionally accepts `opts`, a dict of optional arguments. All of these get
  passed on to `us/seq-limit` to configure convergence checking.

  See `closed-sequence` for more information about Romberg integration, and
  caveats that might apply when using this integration method."}
  closed-integral
  (qc/make-integrator-fn
   qt/single-trapezoid
   closed-sequence))
