(ns wooglife-bot.core
  (:require [telegrambot-lib.core :as tbot]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json])
  (:gen-class))

(defonce api-url
  (or (System/getenv "API_URL")
      "https://api.woog.life"))

(defn retrieve-lake-temperature [lake]
  (let [url (format "%s/lake/%s/temperature" (api-url) (get-in lake [:id]))
        response (client/get url (:as :reader))
        reader (get-in response [:body])
        temp (json/parse-string reader true)]
    (get-in temp [:preciseTemperature])))

(defn retrieve-lakes [lakes]
  (for [lake (get-in lakes [:lakes])]
    (let [temperature (retrieve-lake-temperature lake)]
      {:temperature temperature,
       :name (get-in lake [:name])})))

(defn is-temperature-command
  [msg]
  (let [text (get-in msg [:text])]
    (str/starts-with? text "/temperature")))

(def config
  {:sleep 10000}) ;thread/sleep is in milliseconds

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
   (let [resp (tbot/get-updates bot {:offset offset
                                     :timeout (:timeout config)})]
     (if (contains? resp :error)
       (println "tbot/get-updates error:" (:error resp))
       resp))))

(defn get-lakes
  []
  (let [url (format "%s/lake" (api-url))
        response (client/get url {:as :reader})]
    (with-open [reader (:body response)]
      (let [lakes (json/parse-stream reader true)]
        (retrieve-lakes lakes)))))

(defn format-lake
  [lake]
  (format "%s: %s" (get-in lake [:name]) (get-in lake [:temperature])))

(defn generate-temperature-message
  []
  (let [temperatures (get-lakes)]
    (str "Aktuelle Wassertemperaturen:\n\n"
         (str/join "\n" (for [lake temperatures]
                          (format-lake lake))))))

(defn send-temperature
  [bot chat-id message]
  (let [content {:chat_id chat-id
                 :text message}]
    (tbot/send-message bot content)))

(defn app
  "Retrieve and process chat messages."
  [bot]
  (println "bot service started.")

  (loop []
    (println "checking for chat updates.")
    (let [updates (poll-updates bot @update-id)
          messages (:result updates)]

      ;; Check all messages, if any, for commands/keywords.
      (doseq [msg messages]
        ;(some-handle-msg-fn bot msg) ; your fn that decides what to do with each message. 

        (let [message (get-in msg [:message])]
          (if (is-temperature-command message)
            [(println (send-temperature bot (get-in (get-in message [:from]) [:id]) (generate-temperature-message)))]
            [(println "not a temperature command")]))

        ;; Increment the next update-id to process.
        (-> msg
            :update_id
            inc
            set-id!))

      ;; Wait a while before checking for updates again.
      (Thread/sleep (:sleep config)))
    (recur)))

(defn -main
  "I don't do a whole lot ... yet."
  []
  ;; needs BOT_TOKEN in environment
  (app (tbot/create)))
