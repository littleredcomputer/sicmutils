(ns sicmutils.units.with-units
  (:require [sicmutils.units.units :as u]
            [sicmutils.value :as v]
            [sicmutils.generic :as g]
            [sicmutils.env :as env]))

;; Next step: Acually multiply two values with units. Defer generic /
;; multimethod variants.

(declare ->symbolic with-units=)

(deftype WithUnits [value units])

(defn ^:private ->map [with-units]
  {:value (.value with-units)
   :units (.units with-units)})

(defn ->symbolic [with-units]
  (let [{:keys [value units]} (->map with-units)]
    (g/* value (u/->symbolic units))))



(comment
  (->symbolic (WithUnits. 42 sicmutils.units.scm-api/meter))
  ;; => (* 42 meter)
  )

;; # Naming: value or quantity?
;;
;;  - What does scmutils choose?
;;  - What does Wikipedia[1] talk about?
;;
;; [1]: https://en.wikipedia.org/wiki/International_System_of_Units
;;
;; ## smcutils
;;
;; mentions only quantities in passing.
;;
;; ## Wikipedia
;;
;; tabulates:
;;
;; Symbol |  Name  | Quantity
;;   s    | second |  time
;;
;; Doesn't look like it's using "quantity" as "value" here.
;;
;; # Decision: keep "value" for now.

(defn ->symbolic
  "convert withunits to symbolic expression multiplying quantity with units"
  [with-units])

(defn with-units? [x]
  (isa? x WithUnits))

(defn with-units [value units]
  (WithUnits. value units))

;; Colons inside names isn't typical Clojure. If the functions are private it
;; probably doesn't matter. Sticking close to Scheme for now.
(defn ^:private u:+ [x y])
(defn ^:private u:* [x y])
