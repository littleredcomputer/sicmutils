(ns sicmutils.units.units-test
  (:require [clojure.test :refer [is deftest testing]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]
             #?@(:cljs [:include-macros true])]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :as ct :refer [defspec]]

            [sicmutils.units.units :as u]
            [sicmutils.units.scm-api :as scm-api]
            [sicmutils.generators :as sg]
            [sicmutils.generic :as g]
            [sicmutils.value :as v]))

;;;; Motivation
;; By treating units as symbols (for now), we can make it easier to see what
;; we're doing.

(defn ^:private symbolic= [x y]
  (v/zero? (g/simplify (g/- x y))))

(deftest symbolic-unit-representation
  (testing "unit system must be reflexive if unit comparison can make sense"
    (is (u/system= u/SI u/SI)))

  (testing "generic symbolic equality works (remember simplify)"
    (is
     (symbolic= (g/* 2 'meter 'meter)
                (g/* 'meter 'meter 2))))

  (testing "symbolic meter is equal to symbolic meter"
    (is
     (symbolic= (u/->symbolic scm-api/meter)
                (u/->symbolic scm-api/meter))))

  (testing "symbolic meter is not equal to symbolic kilogram"
    (is
     (not
      (symbolic= (u/->symbolic scm-api/meter)
                 (u/->symbolic scm-api/kilogram))))))

;; TODO generative test for symbolic equality <==> unit equality
;; Requires generator for Units and WithUnits

(deftest inverting-a-unit
  (let [m scm-api/meter]
    (is (u/units= m m))
    (is (u/units= m
                  (-> m u/invert u/invert)))))

;; TODO generative test for inverting values, rather than hard-coding meters
;; Requires generator for Units and WithUnits
