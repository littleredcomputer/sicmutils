(ns sicmutils.units.scm-api
  "Copying the original scmutils API here, so that we can evaluate it. Defer
  finding an idiomatic Clojure API. Consider not pushing this namespace at all."
  (:refer-clojure :exclude [second])
  (:require [sicmutils.value :as v]
            [sicmutils.generic :as g]
            [sicmutils.env :as e]
            [sicmutils.units.units :as u]
            [sicmutils.units.with-units :as wu]))

(def meter    (u/units u/SI [1 0 0 0 0 0 0] 1))
(def kilogram (u/units u/SI [0 1 0 0 0 0 0] 1))
(def second   (u/units u/SI [0 0 1 0 0 0 0] 1))
(def ampere   (u/units u/SI [0 0 0 1 0 0 0] 1))
(def kelvin   (u/units u/SI [0 0 0 0 1 0 0] 1))
(def mole     (u/units u/SI [0 0 0 0 0 1 0] 1))
(def candela  (u/units u/SI [0 0 0 0 0 0 1] 1))

(def & wu/with-units)

;; I like having meter bound as a symbol. It would be nice to avoid the
;; boilerplate. Macro? That's what define-unit-system does in scheme.
;;
;; For now: explicit is better han implicit.

(comment
  (let [meter-squared (u/*units meter meter)]
    [(.exponents meter-squared)
     (.scale meter-squared)])
  ;; => [[2 0 0 0 0 0 0] 1]
  )

(comment
  (e/matrix-by-rows [1  2  3  4  5]
                    [6  7  8  9 10]
                    [11 12 13 14 15])
  ;; this looks like a matrix 🎉
  ;;
  (g/* (e/matrix-by-rows [1 2] [3 4])
       (e/matrix-by-rows [10 20] [30 40]))
  ;; => #object[sicmutils.matrix.Matrix 0x38473da4 "[[70 100] [150 220]]"]

  ;; here we go.
  ;; Now, which function is this?

  ;; How do I control how a deftype prints?
  (u/*units meter meter)
  )
