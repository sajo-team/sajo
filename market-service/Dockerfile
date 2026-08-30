FROM gradle:8-jdk21 AS deps
WORKDIR /app
COPY settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gradlew ./
COPY discovery-service/build.gradle discovery-service/build.gradle
COPY gateway/build.gradle gateway/build.gradle
COPY user-service/build.gradle user-service/build.gradle
COPY market-service/build.gradle market-service/build.gradle
COPY trading-service/build.gradle trading-service/build.gradle
COPY ai-risk-service/build.gradle ai-risk-service/build.gradle
COPY common/build.gradle common/build.gradle
COPY config-server/build.gradle config-server/build.gradle
RUN gradle :market-service:dependencies --no-daemon || true

FROM deps AS build
COPY . .
RUN gradle :market-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/market-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
