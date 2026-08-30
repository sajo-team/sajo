FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :trading-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/trading-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
