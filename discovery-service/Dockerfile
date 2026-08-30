FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :discovery-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/discovery-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
