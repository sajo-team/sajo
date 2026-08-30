FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :user-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/user-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
