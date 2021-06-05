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

(ns sicmutils.simplify
  (:require [sicmutils.expression.analyze :as a]
            [sicmutils.expression :as x]
            [sicmutils.generic :as g]
            [sicmutils.polynomial :as poly]
            [sicmutils.polynomial.factor :as factor]
            [sicmutils.rational-function :as rf]
            [sicmutils.simplify.rules :as rules]
            [sicmutils.value :as v]
            [taoensso.timbre :as log])
  #?(:clj
     (:import (java.util.concurrent TimeoutException))))

(defn- unless-timeout
  "Returns a function that invokes f, but catches TimeoutException;
  if that exception is caught, then x is returned in lieu of (f x)."
  [f]
  (fn [x]
    (try (f x)
         (catch #?(:clj TimeoutException :cljs js/Error) _
           (log/warn
            (str "simplifier timed out: must have been a complicated expression"))
           x))))

(defn ^:no-doc poly-analyzer
  "An analyzer capable of simplifying sums and products, but unable to cancel
  across the fraction bar.
  NOTE: I think this is fpf:analyzer in the scheme code."
  []
  (let [gensym (a/monotonic-symbol-generator "-s-")]
    (a/make-analyzer poly/analyzer gensym)))

(defn ^:no-doc rational-function-analyzer
  "An analyzer capable of simplifying expressions built out of rational
  functions.
  NOTE: This is rcf:analyzer."
  []
  (let [gensym (a/monotonic-symbol-generator "-r-")]
    (a/make-analyzer rf/analyzer gensym)))

(def ^:dynamic *poly-simplify*
  (memoize
   (a/expression-simplifier
    (poly-analyzer))))

(def ^:dynamic *rf-simplify*
  (unless-timeout
   (memoize
    (a/expression-simplifier
     (rational-function-analyzer)))))

(defn hermetic-simplify-fixture
  "Returns the result of executing the supplied `thunk` in an environment where
  the [[*rf-simplify*]] and [[*poly-simplify*]] are not memoized."
  [thunk]
  (binding [*rf-simplify* (a/expression-simplifier
                           (rational-function-analyzer))
            *poly-simplify* (a/expression-simplifier
                             (poly-analyzer))]
    (thunk)))

(defn- simplify-and-flatten [expr]
  (*poly-simplify*
   (*rf-simplify* expr)))

(defn- simplify-until-stable
  [rule-simplify canonicalize]
  (fn [expr]
    (let [new-expr (rule-simplify expr)]
      (if (= expr new-expr)
        expr
        (let [canonicalized-expr (canonicalize new-expr)]
          (cond (= canonicalized-expr expr) expr
                (v/zero?
                 (*poly-simplify*
                  (list '- expr canonicalized-expr)))
                canonicalized-expr
                :else (recur canonicalized-expr)))))))

(defn- simplify-and-canonicalize
  [rule-simplify canonicalize]
  (fn [expr]
    (let [new-expr (rule-simplify expr)]
      (if (= expr new-expr)
        expr
        (canonicalize new-expr)))))

(def ^:private clear-square-roots-of-perfect-squares
  (-> (comp (rules/universal-reductions #'*rf-simplify*)
            factor/root-out-squares)
      (simplify-and-canonicalize simplify-and-flatten)))

(defn- only-if
  "If the supplied `bool` is true, returns `f`, else returns `identity`."
  [bool f]
  (if bool
    f
    identity))

(let [universal-reductions (rules/universal-reductions #'*rf-simplify*)
      sqrt-contract (rules/sqrt-contract #'*rf-simplify*)
      sqrt-expand (rules/sqrt-expand #'*rf-simplify*)
      log-contract (rules/log-contract #'*rf-simplify*)
      sincos-random (rules/sincos-random #'*rf-simplify*)
      sincos-flush-ones (rules/sincos-flush-ones #'*rf-simplify*)]

  (defn simplify-expression
    "Simplifies an expression representing a complex number. TODO say more!"
    [expr]
    (let [syms (x/variables-in expr)
          sqrt? (rules/occurs-in? #{'sqrt} syms)
          full-sqrt? (and rules/*sqrt-factor-simplify?*
                          (rules/occurs-in? #{'sqrt} syms))

          logexp? (rules/occurs-in? #{'log 'exp} syms)
          trig? (rules/occurs-in? #{'sin 'cos 'tan 'cot 'sec 'csc} syms)
          partials? (rules/occurs-in? #{'partial} syms) simple
          (comp (only-if rules/*divide-numbers-through-simplify?*
                         rules/divide-numbers-through)

                (only-if sqrt? clear-square-roots-of-perfect-squares)

                (only-if full-sqrt?
                         (comp (-> (comp universal-reductions sqrt-expand)
                                   (simplify-until-stable simplify-and-flatten))
                               clear-square-roots-of-perfect-squares
                               (-> sqrt-contract
                                   (simplify-until-stable simplify-and-flatten))))

                (only-if trig?
                         (comp (-> (comp universal-reductions rules/sincos->trig)
                                   (simplify-and-canonicalize simplify-and-flatten))
                               (-> rules/complex-trig
                                   (simplify-and-canonicalize simplify-and-flatten))
                               (-> rules/angular-parity
                                   (simplify-and-canonicalize simplify-and-flatten))
                               (-> sincos-random
                                   (simplify-until-stable simplify-and-flatten))
                               (-> rules/sin-sq->cos-sq
                                   (simplify-and-canonicalize simplify-and-flatten))
                               (-> sincos-flush-ones
                                   (simplify-and-canonicalize simplify-and-flatten))

                               (only-if rules/*trig-product-to-sum-simplify?*
                                        (-> rules/trig:product->sum
                                            (simplify-and-canonicalize simplify-and-flatten)))

                               (-> universal-reductions
                                   (simplify-and-canonicalize simplify-and-flatten))
                               (-> sincos-random
                                   (simplify-until-stable simplify-and-flatten))
                               (-> rules/sin-sq->cos-sq
                                   (simplify-and-canonicalize simplify-and-flatten))
                               (-> sincos-flush-ones
                                   (simplify-and-canonicalize simplify-and-flatten))))

                (only-if logexp?
                         (comp (-> universal-reductions
                                   (simplify-and-canonicalize simplify-and-flatten))
                               (-> (comp rules/log-expand
                                         rules/exp-expand)
                                   (simplify-until-stable simplify-and-flatten))
                               (-> (comp log-contract
                                         rules/exp-contract)
                                   (simplify-until-stable simplify-and-flatten))))

                (-> (comp universal-reductions
                          (only-if logexp?
                                   (comp rules/log-expand
                                         rules/exp-expand))
                          (only-if sqrt?
                                   sqrt-expand))
                    (simplify-until-stable simplify-and-flatten))

                (only-if trig?
                         (-> rules/angular-parity
                             (simplify-and-canonicalize simplify-and-flatten)))

                (-> rules/trig->sincos
                    (simplify-and-canonicalize simplify-and-flatten))

                ;; TODO this should happen at the END, only a single time, after
                ;; everything else is done. It's not right to get operator
                ;; multiplication going and then attempt to canonicalize the
                ;; expression, even if it sort of works.
                (only-if partials?
                         (-> rules/canonicalize-partials
                             (simplify-and-canonicalize simplify-and-flatten)))
                simplify-and-flatten)]
      (simple expr))))

(comment
  ;; TODO I THINK we have actual thing somewhere else. We want this dynamic
  ;; variable around.
  (def ^:dynamic *factoring* false)

  ;; Hamiltonians look better if we divide them out.
  (defn ham:simplify [hexp]
    (cond (and (quotient? hexp) *divide-out-terms*)
          (if (sum? (symb:numerator hexp))
            (let [d (symb:denominator hexp)]
              (a-reduce symb:+
                        (map (fn [n]
                               (g/simplify (symb:div n d)))
                             (operands
                              (symb:numerator hexp)))))
            hexp)

          (compound-data-constructor? hexp)
          (cons (operator hexp) (map ham:simplify (operands hexp)))

          :else hexp))

  (define clean-differentials
    ;; TODO clean a CLEANED differential... aren't these all done??
    (rule-simplifier
     (ruleset
      (make-differential-quantity
       [??lterms
        (make-differential-term (? dx) 0)
        ??rterms])
      =>
      (make-differential-quantity [??lterms ??rterms])

      (make-differential-quantity
       [(make-differential-term '() ?x)]) => ?x

      (make-differential-quantity []) => 0)))

  (define (flush-literal-function-constructors expr)
    (if (pair? expr)
      (if (eq? (car expr) 'literal-function)
        (if (and (pair? (cadr expr)) (eq? (caadr expr) 'quote))
          (flush-literal-function-constructors (cadadr expr))
          (cadr expr))
        (cons (flush-literal-function-constructors (car expr))
              (flush-literal-function-constructors (cdr expr))))
      expr))

  (defn simplify [exp]
    ((access clean-differentials rule-environment)
     (flush-derivative
      (flush-literal-function-constructors
       (ham:simplify
        ((if *factoring* poly:factor (fn [expr] expr))
         (g:simplify exp)))))))

  ;; Is this enough? move to simplify.
  (define (careful-simplify e)
    (simplify e)))
