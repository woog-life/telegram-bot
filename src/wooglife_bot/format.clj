(ns wooglife-bot.format
  (:require
    [java-time.api :as jt]
    [wooglife-bot.helper :as h]))

(defn format-time
  ([time] (format-time time "HH:mm dd.MM"))
  ([time format] (jt/format format time)))

(defn format-high-low-tide
  [is-high-tide]
  (if is-high-tide "HW" "NW"))

(defn format-tide
  [tide timezone]
  (format "  %s %s (%sm)"
          (format-time (h/parse-time (:time tide) timezone))
          (format-high-low-tide (:isHighTide tide))
          (:height tide)))

(defn format-temperature
  [lake]
  (let [temperature (:preciseTemperature (:temperatureData lake))]
  (format "%s%s"
          (:name lake)
          (if (nil? temperature)
            ""
            (format ": %s°C" temperature)))))

(defn format-tides
  [tides timezone]
  (let [x (h/join "\n" (map #(format-tide % timezone) tides))]
    x))

(defn format-full-featured-lake
  [lake]
  (h/join "\n"
        [(format-temperature lake)
         (format-tides (:tides lake) (:timeZoneId lake))]))

(defn format-lakes
  [lakes]
  (h/join "\n" (map format-full-featured-lake lakes)))
