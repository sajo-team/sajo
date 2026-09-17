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
COPY operation-service/build.gradle operation-service/build.gradle
COPY libs/common/build.gradle libs/common/build.gradle
COPY libs/common-kafka/build.gradle libs/common-kafka/build.gradle
COPY libs/common-redis/build.gradle libs/common-redis/build.gradle
COPY libs/common-jwt/build.gradle libs/common-jwt/build.gradle
COPY config-server/build.gradle config-server/build.gradle
RUN gradle :operation-service:dependencies --no-daemon || true

FROM deps AS build
COPY . .
RUN gradle :operation-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/operation-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
