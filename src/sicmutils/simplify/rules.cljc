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

(ns sicmutils.simplify.rules
  (:require [pattern.rule :refer [ruleset rule-simplifier]
             #?@(:cljs [:include-macros true])]
            [sicmutils.generic :as g]
            [sicmutils.value :as v]))

(defn ^:private more-than-two? [x]
  (and (v/number? x) (> x 2)))

(defn ^:private at-least-two? [x]
  (and (v/number? x) (>= x 2)))

(defn ^:private even-integer? [x]
  (and (v/integral? x) (v/zero? (g/modulo x 2))))

(defn ^:private odd-integer? [x]
  (and (v/integral? x)
       (not (v/zero? (g/modulo x 2)))))

(def sin-sq->cos-sq
  (rule-simplifier
   (ruleset
    (expt (sin :x) (:? n at-least-two?))
    => (* (expt (sin :x) (:? #(- (% 'n) 2)))
          (- 1 (expt (cos :x) 2))))))

(def ^:private split-high-degree-cosines
  (ruleset
   (* :f1* (expt (cos :x) (:? n more-than-two?)) :f2*)
   => (* (expt (cos :x) 2)
         (expt (cos :x) (:? #(- (% 'n) 2)))
         :f1*
         :f2*)

   (+ :a1* (expt (cos :x) (:? n more-than-two?)) :a2*)
   => (+ (* (expt (cos :x) 2)
            (expt (cos :x) (:? #(- (% 'n) 2))))
         :a1*
         :a2*)))

(def ^:private split-high-degree-sines
  (ruleset
   (* :f1* (expt (sin :x) (:? n more-than-two?)) :f2*)
   => (* (expt (sin :x) 2)
         (expt (sin :x) (:? #(- (% 'n) 2)))
         :f1*
         :f2*)

   (+ :a1* (expt (sin :x) (:? n more-than-two?)) :a2*)
   => (+ (* (expt (sin :x) 2)
            (expt (sin :x) (:? #(- (% 'n) 2))))
         :a1*
         :a2*)))

;; The following two functions expand and contract the sine and cosine sum
;; identities.

(def trig-difference-expand
  (rule-simplifier
   (ruleset
    (cos (+ :x :y))
    => (+ (* (cos :x) (cos :y)) (* -1 (sin :x) (sin :y)))

    (sin (+ :x (* -1 :y)))
    => (+ (* (sin :x) (cos :y)) (* -1 (cos :x) (sin :y)))

    (sin (+ :x :y))
    => (+ (* (sin :x) (cos :y)) (* (cos :x) (sin :y)))

    (cos (+ :x (* -1 :y)))
    => (+ (* (cos :x) (cos :y)) (* (sin :x) (sin :y))))))

(def trig-difference-contract
  (rule-simplifier
   (ruleset
    (* :l* (expt (sin :x) :n) :r*)
    => (* :l* (:?? #(repeat (% :n) (list 'sin (% :x)))) :r*)

    (* :l* (expt (cos :x) :n) :r*)
    => (* :l* (:?? #(repeat (% :n) (list 'cos (% :x)))) :r*)

    ;; sin(x - y) == sin(x)cos(y) - cos(x)sin(y)
    (+ :ol*
       (* :l* (sin :x) :m* (cos :y) :r*)
       :om*
       (* -1 :l* (cos :x) :m* (sin :y) :r*)
       :or*)
    => (+ :ol*
          (* :l* (sin (+ :x (* -1 :y))) :m* :r*)
          :om*
          :or*)

    ;; cos(x + y) == cos(x)cos(y) - sin(x)sin(y)
    (+ :ol*
       (* :l* (cos :x) :m* (cos :y) :r*)
       :om*
       (* -1 :l* (sin :x) :m* (sin :y) :r*)
       :or*)
    => (+ :ol*
          (* :l* (cos (+ :x :y)) :m* :r*)
          :om*
          :or*)

    ;; sin(x + y) == sin(x)cos(y) + cos(x)sin(y)
    (+ :ol*
       (* :l* (sin :x) :m* (cos :y) :r*)
       :om*
       (* :l* (cos :x) :m* (sin :y) :r*)
       :or*)
    => (+ :ol*
          (* :l* (sin (+ :x :y)) :m* :r*)
          :om*
          :or*)

    ;; cos(x - y) == cos(x)cos(y) + sin(x)sin(y)
    (+ :ol*
       (* :l* (cos :x) :m* (cos :y) :r*)
       :om*
       (* :l* (sin :x) :m* (sin :y) :r*)
       :or*)
    => (+ :ol*
          (* :l* (cos (+ :x (* -1 :y))) :m* :r*)
          :om*
          :or*))))

(def simplify-square-roots
  (rule-simplifier
   (ruleset
    (expt (sqrt :x) (:? n even-integer?))
    => (expt :x (:? #(/ (% 'n) 2)))

    (sqrt (expt :x (:? n even-integer?)))
    => (expt :x (:? #(/ (% 'n) 2)))

    (expt (sqrt :x) (:? n odd-integer?))
    => (* (sqrt :x) (expt :x (:? #(/ (- (% 'n) 1) 2))))

    (/ :x (sqrt :x)) => (sqrt :x)

    (/ (sqrt :x) :x) => (/ 1 (sqrt :x))

    (/ (* :u* :x :v*) (sqrt :x))
    =>
    (* :u* (sqrt :x) :v*)

    (/ (* :u* (sqrt :x) :v*) :x)
    =>
    (/ (* :u* :v*) (sqrt :x))

    (/ :x (* :u* (sqrt :x) :v*))
    =>
    (/ (sqrt :x) (* :u* :v*))

    (/ (sqrt :x) (* :u* :x :v*))
    =>
    (/ 1 (* :u* (sqrt :x) :v*))

    (/ (* :p* :x :q*)
       (* :u* (sqrt :x) :v*))
    =>
    (/ (* :p* (sqrt :x) :q*)
       (* :u* :v*))

    (/ (* :p* (sqrt :x) :q*)
       (* :u* :x :v*))
    =>
    (/ (* :p* :q*)
       (* :u* (sqrt :x) :v*))

    ;; Following are the new rules we added to approach
    ;; the simplification of the time-invariant-canonical
    ;; test.

    ;; ... (sqrt a) ... (sqrt b) ... => ... (sqrt a b)
    (* :f1* (sqrt :a) :f2* (sqrt :b) :f3*)
    => (* :f1* :f2* :f3* (sqrt (* :a :b)))

    ;; (/ (* ... (sqrt a) ...)
    ;;    (* ... (sqrt b) ...)  => ... (sqrt (/ a b)) ... / ... ...
    (/ (* :f1* (sqrt :a) :f2*)
       (* :g1* (sqrt :b) :g2*))
    => (/ (* :f1* :f2* (sqrt (/ :a :b)))
          (* :g1* :g2*))


    ;; others to follow
    )))

(def sqrt-expand
  (rule-simplifier
   (ruleset

    ;; "distribute the radical sign across products and quotients.
    ;; but doing this may allow equal subexpressions within the
    ;; radicals to cancel in various ways. The companion rule
    ;; sqrt-contract reassembles what remains."

    ;; Scmutils, in each of these expansions, will `asssume!`
    ;; that the expressions named :x and :y are non-negative

    (sqrt (* :x :y)) => (* (sqrt :x) (sqrt :y))

    (sqrt (* :x :y :ys*)) => (* (sqrt :x) (sqrt (* :y :ys*)))

    (sqrt (/ :x :y)) => (/ (sqrt :x) (sqrt :y))

    (sqrt (/ :x :y :ys*)) => (/ (sqrt :x) (sqrt (* :y :ys*))))))

(def sqrt-contract
  (rule-simplifier
   (ruleset

    ;; scmutils note: in scmutils, each of these rules checks to see whether,
    ;; after sub-simplification, x and y are equal, and if so, the opportunity
    ;; is taken to subsitute a simpler result.
    ;;
    ;; It could be that we don't need that, if there were a rule (for example)
    ;; to replace (* (sqrt x) (sqrt x)) with x. I tend to think that running the
    ;; simplifier on interior subexpressions is a dubious idea given how
    ;; much "churn" there is already waiting for the rulesets to stabilize

    ;; Scmutils, in each of these contractions, will `asssume!`
    ;; that the expressions named :x and :y are non-negative

    (* :a* (sqrt :x) :b* (sqrt :y) :c*)
    => (* :a* :b* :c* (sqrt (* :x :y)))

    (/ (sqrt :x) (sqrt :y))
    => (sqrt (/ :x :y))

    (/ (* :a* (sqrt :x) :b*) (sqrt :y))
    => (* :a* :b* (sqrt (/ :x :y)))

    (/ (sqrt :x) (* :a* (sqrt :y) *b*))
    => (/ (sqrt (/ :x :y)) (* :a* :b*))

    (/ (* :a* (sqrt :x) :b*)
       (* :c* (sqrt :y) :d*))
    => (/ (* :a* :b* (sqrt (/ :x :y)))
          (* :c* :d*))

    )))

(def complex-trig
  ;; TODO: clearly more of these are needed.
  (rule-simplifier
   (ruleset
    (cos (* :z (complex 0.0 1.0)))
    => (cosh :z)

    (sin (* :z (complex 0.0 1.0)))
    => (* (complex 0.0 1.0) (sinh :z))

    ;; Does this really belong here?
    ;; It works by reducing n mod 4 and then indexing into [1 i -1 -i].
    (expt (complex 0.0 1.0) (:? n v/integral?))
    => (:? #([1 '(complex 0 1) -1 '(complex 0 -1)] (mod (% 'n) 4))))))

(def divide-numbers-through
  (rule-simplifier
   (ruleset
    (* 1 :factor)
    => :factor

    (* 1 :factors*)
    => (* :factors*)

    (/ (:? n v/number?) (:? d v/number?))
    => (:? #(g// (% 'n) (% 'd)))

    (/ (+ :terms*) (:? d v/number?))
    => (+ (:?? #(map (fn [n] `(~'/ ~n ~(% 'd))) (% :terms*)))))))

(def ^:private flush-obvious-ones
  (ruleset
   (+ :a1* (expt (sin :x) 2) :a2* (expt (cos :x) 2) :a3*)
   => (+ 1 :a1* :a2* :a3*))
  ;; are sines always before cosines after we poly simplify?
  ;; they are in scmutils, so we should be alert for this.
  ;; in scmutils, there are a couple of others that involve rcf:simplify,
  ;; which we dont' have, and we don't know if pcf:simplify is an
  ;; acceptable substitute here; and we don't have a method for
  ;; pasting the value of a predicate into a rule, so this is far from
  ;; complete.
  )

(def trig->sincos
  (rule-simplifier
   (ruleset
    ;; GJS has other rules: to map cot, sec and csc to sin/cos, but
    ;; I don't think we need those since we transform those to sin/cos
    ;; in the env namespace.
    (tan :x) => (/ (sin :x) (cos :x)))))

;; note the difference in interface between rulesets and rule simplifiers.
;; rulesets return nil when they're not applicable (unless you specify a
;; custom fail continuation). Rule-simplifiers pass expressions through.

(def sincos->trig
  (rule-simplifier
   (ruleset
    ;; undoes the effect of trig->sincos
    (/ (sin :x) (cos :x))
    => (tan :x)

    (/ (sin :x) (* :d1* (cos :x) :d2*))
    => (/ (tan :x) (* :d1* :d2*))

    (/ (* :n1* (sin :x) :n2*)
       (* :d1* (cos :x) :d2*))
    => (/ (* :n1* (tan :x) :n2*)
          (* :d1* :d2*)))))

(def triginv
  (rule-simplifier
   (ruleset
    (sin (asin :x))          => :x
    (asin (sin :x))          => :x
    (sin (atan :y :x))       => (/ :y (sqrt (+ (expt :x 2) (expt :y 2))))
    (cos (atan :y :x))       => (/ :x (sqrt (+ (expt :x 2) (expt :y 2))))
    (cos (asin :t))          => (sqrt (- 1 (square :t)))
    )
   (ruleset
    (acos (cos :x))          => :x
    (atan (tan :x))          => :x
    (atan (sin :x) (cos :x)) => :x
    (atan (* :c (sin :x)) (* :c (cos :x))) => :x)))

(def sincos-flush-ones
  (rule-simplifier split-high-degree-cosines
                   split-high-degree-sines
                   flush-obvious-ones))

(defn universal-reductions [x]
  (triginv x))

(def canonicalize-partials
  (rule-simplifier
   (ruleset
    ;; Convert nests into products.
    ((partial :i*) ((partial :j*) :f))
    =>
    ((* (partial :i*) (partial :j*)) :f)

    ((partial :i*) ((* (partial :j*) :more*) :f))
    =>
    ((* (partial :i*) (partial :j*) :more*) :f)

    ;; Gather exponentiated partials into products
    ((expt (partial :i*) :n) ((partial :j*) :f))
    =>
    ((* (expt (partial :i*) :n) (partial :j*)) :f)

    ((partial :i*) ((expt (partial :j*) :n) :f))
    =>
    ((* (partial :i*) (expt (partial :j*) :n)) :f)

    ((expt (partial :i*) :n) ((expt (partial :j*) :m) :f))
    =>
    ((* (expt (partial :i*) :n) (expt (partial :j*) :m)) :f)

    ;; Same idea, trickier when some accumulation has already occurred.
    ((expt (partial :i*) :n) ((* (partial :j*) :more*) :f))
    =>
    ((* (expt (partial :i*) :n) (partial :j*) :more*) :f)

    ((partial :i*) ((* (expt (partial :j*) :m) :more*) :f))
    =>
    ((* (partial :i*) (expt (partial :j*) :m) :more*) :f)

    ((expt (partial :i*) :n) ((* (expt (partial :j*) :m) :more*) :f))
    =>
    ((* (expt (partial :i*) :n) (expt (partial :j*) :m) :more*) :f)

    ;; example:
    #_(((* (partial 2 1) (partial 1 1)) FF) (up t (up x y) (down p_x p_y)))
    ;; since the partial indices in the outer derivative are lexically
    ;; greater than those of the inner, we canonicalize by swapping the
    ;; order. This is the "equality of mixed partials."
    (((* :xs* (partial :i*) :ys* (partial :j*) :zs*) :f) :args*)
    #(neg? (compare (% :i*) (% :j*)))
    (((* :xs* (partial :j*) :ys* (partial :i*) :zs*) :f) :args*))))
