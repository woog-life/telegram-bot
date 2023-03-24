FROM clojure:temurin-19-alpine as builder

RUN apk update && apk add leiningen

WORKDIR /app

ADD src/ src/
ADD project.clj project.clj

RUN lein uberjar

FROM clojure:temurin-19-alpine

COPY --from=builder /app/target/default+uberjar/wooglife-bot-0.1.0-SNAPSHOT-standalone.jar main.jar

CMD ["java", "-jar", "main.jar"]
