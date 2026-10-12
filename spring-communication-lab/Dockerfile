FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
ARG SERVICE
RUN mvn -B -ntp -pl ${SERVICE} -am package -DskipTests
FROM eclipse-temurin:17-jre
WORKDIR /app
ARG SERVICE
COPY --from=build /build/${SERVICE}/target/${SERVICE}-1.0.0.jar app.jar
USER 10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
