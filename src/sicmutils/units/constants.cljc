(ns sicmutils.units.constants
  (:require [sicmutils.units.scm-api :as scm-api]
            [sicmutils.units.units :as u]))

(comment
  ;; Step 1: Define C
  (define-constant ':c "c" "speed of light"
    2.99792458e8
    (/ &meter &second))

  (scm-api/&
   2.99792458e8
   (u/*units scm-api/meter
             (u/invert scm-api/meter)))
  ;; => #object[sicmutils.units.with_units.WithUnits 0x77251e6b "sicmutils.units.with_units.WithUnits@77251e6b"]

  ;; requirement: decent printing.
  ;; Use the same trick as we did with units.
  ;;
  ;; TODO: the "symbolic trick" isn't readable back into the unit system.
  ;; ACTION: Define a symbolic-> action that can take symbolic values back into "canonical form"
  ;;
  ;; ... this is actually a good idea.

  (u/symbolic-> '(* 1 meter (/ 1 second)))

  ;; 1. Requires reqursive symbolic substitution. This probably exists somewhere
  ;;    in sicmutils already.
  ;; 2. Requires units to "work properly as values".
  ;; 3. Further emphasises "symbolic interface of units", ref https://github.com/sicmutils/sicmutils/issues/181#issuecomment-751669339
  )
