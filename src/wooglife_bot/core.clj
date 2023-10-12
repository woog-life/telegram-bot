(ns wooglife-bot.core
  (:require [clojure.string :as str]
            [telegrambot-lib.core :as tbot]
            [wooglife-bot.format :as f]
            [wooglife-bot.helper :as h]
            [wooglife-bot.api :as api])
  (:gen-class)
  (:import (wooglife_bot.models Lake)))


(def config
  {:sleep 3000})

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

(defonce bot-token (System/getenv "TOKEN"))
;(if (or (nil? bot-token) (empty? bot-token))
;  ((println "`TOKEN` env variable is required")
;   (System/exit 1)))
(defonce bot (tbot/create bot-token))

(defonce notifier-ids (clojure.string/split (System/getenv "NOTIFIER_IDS") #","))

(defn assemble-lake-features
  ([lake] (assemble-lake-features lake true true))
  ([lake assemble-temperature assemble-tides]
   (let [featured-lake (Lake. (:id lake)
                              (:name lake)
                              (:features lake)
                              (:timeZoneId lake))]
     (as->
       (if (and (h/lake-supports-temperature lake) assemble-temperature)
         (assoc featured-lake :temperatureData (api/get-lake-temperature (:id lake)))
         featured-lake) $
       (if (and (h/lake-supports-tides lake) assemble-tides)
         (assoc $ :tides (api/get-lake-tides (:id lake)))
         $)))))

(defn assemble-full-featured-lakes
  [lakes]
  (map assemble-lake-features lakes))

(defn assemble-temperature-lakes
  [lakes]
  (let [lakes (filter h/lake-supports-temperature lakes)]
  (map #(assemble-lake-features % true false) lakes)))

(defn assemble-tide-lakes
  [lakes]
  (let [lakes (filter h/lake-supports-tides lakes)]
  (map #(assemble-lake-features % false true) lakes)))

(defn generate-update-message
  ([] (generate-update-message (api/get-lakes)))
  ([lakes]
   (f/format-lakes
     (assemble-full-featured-lakes lakes))))

(defn send-message
  [chat-id message]
  (println "send to telegram-chat" chat-id)
  (tbot/send-message bot {:chat_id chat-id
                          :text    message}))

(defn handle-feature-command
  [message assemble-fn]
  (let [args (rest (str/split (:text message) #" "))
        lakeNameFilter (h/join " " args)
        lakes (api/get-lakes)
        lakes (h/filter-lakes-by-name lakes lakeNameFilter)]
    (send-message
      (:id (:chat message))
      (f/format-lakes
        (assemble-fn lakes)))))

(defn handle-temperature-command
  [message]
  (handle-feature-command message assemble-temperature-lakes))

(defn handle-tides-command
  [message]
  (handle-feature-command message assemble-tide-lakes))

(defn app
  "Retrieve and process chat messages."
  []
  (println "bot service started.")

  (loop []
    (let [updates (poll-updates bot @update-id)
          messages (:result updates)]

      (doseq [msg messages]
        (let [message (get-in msg [:message])]
          (if (h/is-temperature-command message)
            (handle-temperature-command message)
            #_{:clj-kondo/ignore [:missing-else-branch]}
            (if (h/is-tides-command message)
              (handle-tides-command message))))

        ;; Increment the next update-id to process.
        (-> msg
            :update_id
            inc
            set-id!))

      (Thread/sleep (:sleep config)))
    (recur)))

(defn send-update-message
  [chat-ids]
  (let [message (generate-update-message)]
    (doall (map #(send-message % message) chat-ids))))

(defn -main
  [& args]
    (if (empty? args)
       [(app)]
       [(send-update-message notifier-ids)]))
