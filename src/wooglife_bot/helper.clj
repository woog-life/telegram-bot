(ns wooglife-bot.helper
  (:require
    [java-time.api :as jt]
    [clojure.string :as str]))

(defn join
  [sep coll]
  (str/join sep (remove empty? (remove nil? coll))))

(defn parse-time
  [time timezone]
  (let [instant (jt/instant time)]
    (jt/zoned-date-time instant timezone)))

(defn lake-name-includes
  [lake filter]
  (clojure.string/includes? (clojure.string/lower-case (:name lake)) (clojure.string/lower-case filter)))

(defn filter-lakes-by-name
  [lakes subs]
  (filter #(lake-name-includes % subs) lakes))

(defn lake-has-id
  [lake id]
  (= id (:id lake)))

(defn lake-has-ids-not
  [lake ids]
  (let [fids (filter
               (fn [id] (lake-has-id lake id))
               ids)]
    (empty? fids)))

(defn filter-lakes-by-ids-not
  [lakes ids]
  (filter
    (fn [lake]
      (lake-has-ids-not lake ids))
    lakes))

(defn lake-supports-feature
  [lake feature]
  (contains? (set (:features lake)) feature))

(defn lake-supports-temperature
  [lake]
  (lake-supports-feature lake "temperature"))

(defn lake-supports-tides
  [lake]
  (lake-supports-feature lake "tides"))

(defn is-named-command
  [msg command]
  (as-> (:text msg) $
        (if (nil? $)
          false
          (str/starts-with? $ command))))

(defn is-temperature-command
  "simply checks whether the message text starts with `/temperature`"
  [msg]
  (is-named-command msg "/temperature"))

(defn is-tides-command
  "simply checks whether the message text starts with `/tides"
  [msg]
  (is-named-command msg "/tides"))

(defn is-old
  [lake]
  (println lake)
  (let [temperatureData (:temperatureData lake)
        lastUpdateTime (parse-time (:time temperatureData) (:timeZoneId lake))
        now (jt/zoned-date-time)
        cutoff (jt/minus now (jt/minutes 1439))]            ; 23h59m
    (jt/before? lastUpdateTime cutoff)))

(defn filter-old-lake-data
  [lakes]
  (filter #(not (is-old %)) lakes))
