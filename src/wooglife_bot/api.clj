(ns wooglife-bot.api
  (:require
    [clj-http.client :as client]
    [cheshire.core :as json]
    [wooglife-bot.models :as models]))

(defonce api-url
         (or (System/getenv "API_URL")
             "https://api.woog.life"))

(defn api-call
  [path]
  (as->
    (format "%s/%s" api-url path) $
    (client/get $ (:as :reader))
    (get-in $ [:body])
    (json/parse-string $ true)))

(defn get-lakes
  []
  (let [lakes (get-in (api-call "lake") [:lakes])]
    (map models/api-lake-map-to-record lakes)))

(defn get-lake-output
  [uuid]
  (models/api-lake-map-to-record (api-call (format "lake/%s" uuid))))

(defn get-lake-temperature
  ([uuid] (get-lake-temperature uuid 2 "DE"))
  ([uuid precision formatRegion]
   (->
     (api-call (format "lake/%s/temperature?precision=%d&formatRegion=%s" uuid precision formatRegion))
     models/api-temperature-to-record)))

(defn get-lake-tides
  [uuid]
  (->
    (api-call (format "lake/%s/tides" uuid))
    models/api-tides-to-record))
