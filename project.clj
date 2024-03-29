(defproject wooglife-bot "0.1.0-SNAPSHOT"
  :description "post temperature updates on demand to telegram chat"
  :url "github.com/woog-life/telegram-bot"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [telegrambot-lib "2.7.0"]
                 [cheshire "5.12.0"]
                 [clj-http "3.12.3"]
                 [clojure.java-time "1.4.2"]]
  :main ^:skip-aot wooglife-bot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
