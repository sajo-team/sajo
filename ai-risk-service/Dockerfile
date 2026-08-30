FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :ai-risk-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/ai-risk-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
