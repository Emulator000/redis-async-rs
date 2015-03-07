;; Copyright 2015 Ben Ashford
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns redis-async.protocol
  (:require [gloss.core :refer :all]))

;; unify with real-frame
(def resp-frame-out (string :utf-8 :delimiters ["\r\n"]))

;; Reading

(defcodec resp-type
  (enum :byte {:str \+ :err \- :int \: :bulk-str \$ :ary \*}))

(defcodec resp-str
  (string :utf-8 :delimiters ["\r\n"]))

(defcodec resp-err
  (compile-frame (string :utf-8 :delimiters ["\r\n"])
                 (fn [err] (:error err))
                 (fn [str] {:error str})))

(defcodec resp-int
  (string-integer :utf-8 :delimiters ["\r\n"]))

(defcodec resp-bulk-str
  (header
   (string-integer :utf-8 :delimiters ["\r\n"])
   (fn [str-size]
     (if (< str-size 0)
       (compile-frame []
                      (fn [_] [])
                      (fn [_] :nil))
       (string :utf-8 :length str-size :suffix "\r\n")))
   (fn [str]
     (if (keyword? str)
       -1
       (count str)))))

(declare resp-frame)

(defcodec resp-ary
  (header
   (string-integer :utf-8 :delimiters ["\r\n"])
   (fn [ary-size]
     (compile-frame
      (if (= ary-size 0)
        []
        (repeat ary-size resp-frame))
      (fn [ary] (mapv #(if (nil? %) :nil %) ary))
      (fn [ary] (mapv #(if (= % :nil) nil %) ary))))
   (fn [ary]
     (count ary))))

(def ^:private resp-frames
  {:str      resp-str
   :err      resp-err
   :int      resp-int
   :bulk-str resp-bulk-str
   :ary      resp-ary})

(defcodec resp-frame
  (header resp-type
          resp-frames
          (fn [data]
            (cond
             (or (vector? data) (seq? data))
             :ary

             (or (keyword? data) (string? data))
             :bulk-str

             (integer? data)
             :int))))
