(ns wooglife-bot.core
  (:require [telegrambot-lib.core :as tbot]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [java-time.api :as jt])
  (:gen-class))

(defonce api-url
  (or (System/getenv "API_URL")
      "https://api.woog.life"))

(println (format "use `%s` as api url", api-url))

(defn retrieve-lake-temperature
  "calls the /temperature endpoint for the given lake and returns a map with :name and :temperature (this is the preciseTemperature key from the api)"
  [lake]
  (as->
   (format "%s/lake/%s/temperature" api-url (get-in lake [:id])) $
    (client/get $ (:as :reader))
    (get-in $ [:body])
    (json/parse-string $ true)
    (get-in $ [:preciseTemperature])))

(defn parse-time
  [time]
  (let [instant (jt/instant time)]
    (jt/zoned-date-time instant "Europe/Berlin")))

(defn retrieve-lake-temperatures
  "calls the /temperature endpoint for all given lakes, returns the results as a list"
  [lakes]
  (for [lake lakes]
    (let [temperature (retrieve-lake-temperature lake)]
      {:temperature temperature,
       :name        (get-in lake [:name])})))

(defn retrieve-lake-tides
  [lake]
  (as->
   (format "%s/lake/%s/tides" api-url (get-in lake [:id])) $
    (client/get $ (:as :reader))
    (get-in $ [:body])
    (json/parse-string $ true)
    (get-in $ [:extrema])))

(defn is-temperature-command
  "simply checks whether the message text starts with `/temperature`"
  [msg]
  (as-> (get-in msg [:text]) $
    #_{:clj-kondo/ignore [:missing-else-branch]}
    (if-not (nil? $)
      (str/starts-with? $ "/temperature"))))

(defn is-tides-command
  "simply checks whether the message text starts with `/tides"
  [msg]
  (as-> (get-in msg [:text]) $
    #_{:clj-kondo/ignore [:missing-else-branch]}
    (if-not (nil? $)
      (str/starts-with? $ "/tides"))))

(def config
  {:sleep 3000})                                           ;thread/sleep is in milliseconds

(defonce update-id (atom nil))

(defn set-id!
  "Sets the update id to process next as the the passed in `id`."
  [id]
  (reset! update-id id))

(defn poll-updates
  "Long poll for recent chat messages from Telegram."
  ([bot]
   (poll-updates bot nil))

  ([bot offset]
   (let [resp (tbot/get-updates bot {:offset  offset
                                     :timeout (:timeout config)})]
     (if (contains? resp :error)
       (println "tbot/get-updates error:" (:error resp))
       resp))))

(defn lake-name-matches-filter
  [lake-name args]
  (->> (map str/lower-case args)
       (clojure.core/filter (fn
                              [arg]
                              (str/includes? lake-name arg)))))


(defn filter-lake
  [lake args]
  (as-> (get-in lake [:name]) $
    (str/lower-case $)
    (lake-name-matches-filter $ args)
    (not (empty? $))))

(defn filter-lakes
  [lakes args]
  (->> lakes
       (clojure.core/filter (fn
                              [lake]
                              (filter-lake lake args)))))

(defn get-lakes
  [args]
  (let [url (format "%s/lake" api-url)
        response (client/get url {:as :reader})]
    (with-open [reader (:body response)]
      (let [response (json/parse-stream reader true)
            lakes (get-in response [:lakes])]
        (if (empty? args)
          lakes
          (doall (filter-lakes lakes args)))))))

(defn format-lake
  "{name}: {temperature}"
  [lake]
  (format "%s: %s" (get-in lake [:name]) (get-in lake [:temperature])))

(defn generate-temperature-message
  [args]
  (let [lakes (get-lakes args)
        temperatures (retrieve-lake-temperatures lakes)]
    (str "Aktuelle Wassertemperaturen:\n\n"
         (str/join "\n" (for [lake temperatures]
                          (format-lake lake))))))

(defn format-time
  [time]
  (jt/format "HH:mm dd.MM" time))

(defn format-high-low-tide
  [is-high-tide]
  (if is-high-tide "HW" "NW"))

(defn format-tide-extrema
  [tide-information]
  (format "%s %s (%sm)" (format-time (parse-time (get-in tide-information [:time]))) (format-high-low-tide (get-in tide-information [:isHighTide])) (get-in tide-information [:height])))

(defn format-tides
  [lake-map]
  (let [lake (get-in lake-map [:lake])
        tides (get-in lake-map [:tides])]
    (format "%s\n%s" (get-in lake [:name])
            (str/join "\n" (for [tide tides]
                             (format-tide-extrema tide))))))

(defn retrieve-tides
  [lakes]
  (for [lake lakes]
    {:lake lake
     :tides (retrieve-lake-tides lake)}))

(defn filter-by-supported-feature
  [lakes feature]
  (filter (fn [lake]
            (contains? (set (get-in lake [:features])) feature))
          lakes))

(defn generate-tides-message
  [args]
  (let [lakes (get-lakes args)
        tides (retrieve-tides (filter-by-supported-feature lakes "tides"))]
    (str "Aktuelle Tiden-Informationen:\n\n"
         (str/join "\n\n" (for [tide tides]
                            (format-tides tide))))))

(defn send-message
  [bot chat-id message]
  (println "send to telegram-chat" chat-id)
  (tbot/send-message bot {:chat_id chat-id
                          :text    message}))

(defn parse-command-args
  [message]
  (as-> (get-in message [:text]) $
    (rest (str/split $ #" "))))

(defn handle-temperature-command
  [bot message]
  (println "handle temperature command")
  (let
   [chat-id (get-in (get-in message [:chat]) [:id])
    args (parse-command-args message)
    msg (generate-temperature-message args)]
    (if (= msg "Aktuelle Wassertemperaturen:\n\n")
      (println "don't send temperature due to no content" msg)
      (println (send-message bot chat-id msg)))))

(defn handle-tides-command
  [bot message]
  (let
   [chat-id (get-in (get-in message [:chat]) [:id])
    args (parse-command-args message)
    msg (generate-tides-message args)]
    (if (= msg "Aktuelle Tiden-Informationen:\n\n")
      (println "don't send tides due to no content" msg)
      (println (send-message bot chat-id msg)))))

(defn app
  "Retrieve and process chat messages."
  [bot]
  (println "bot service started.")

  (loop []
    (let [updates (poll-updates bot @update-id)
          messages (:result updates)]

      (doseq [msg messages]
        (let [message (get-in msg [:message])]
          #_{:clj-kondo/ignore [:missing-else-branch]}
          (if (is-temperature-command message)
            (handle-temperature-command bot message)
            (if (is-tides-command message)
              (handle-tides-command bot message))))

        ;; Increment the next update-id to process.
        (-> msg
            :update_id
            inc
            set-id!))

      (Thread/sleep (:sleep config)))
    (recur)))

(defn -main
  []
  (let [token (System/getenv "TOKEN")]
    (if (= token nil)
      [(println "no token defined in environment")
       (System/exit 1)]
      [(app (tbot/create token))])))
