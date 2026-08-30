FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :config-server:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/config-server/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
