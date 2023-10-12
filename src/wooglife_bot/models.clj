(ns wooglife-bot.models)

(defrecord LakeOutput [id name features timeZoneId])

(defrecord TemperatureData [time localTime temperature preciseTemperature])

(defrecord TidalExtremumOutputData [isHighTide time localTime height])

(defrecord Lake [id name features timeZoneId])

(defn api-tide-to-record
  [tide]
  (TidalExtremumOutputData. (:isHighTide tide)
                            (:time tide)
                            (:localTime tide)
                            (:height tide)))

(defn api-tides-to-record
  [extrema]
  (map api-tide-to-record (:extrema extrema)))

(defn api-lake-map-to-record
  [lake]
  (LakeOutput. (:id lake)
               (:name lake)
               (:features lake)
               (:timeZoneId lake)))

(defn api-temperature-to-record
  [temperature]
  (TemperatureData. (:time temperature)
                    (:localTime temperature)
                    (:temperature temperature)
                    (:preciseTemperature temperature)))
