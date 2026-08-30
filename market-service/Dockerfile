FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :market-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/market-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
