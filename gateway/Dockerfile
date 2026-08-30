FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :gateway:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/gateway/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
