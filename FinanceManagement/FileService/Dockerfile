# --- Stage 1: Build ---
FROM gradle:8.5-jdk21 AS build

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src ./src

RUN gradle build -x test --no-daemon

# --- Stage 2: Run ---
FROM eclipse-temurin:21-jdk-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# Download OpenTelemetry Java agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/libs/opentelemetry-javaagent-2.11.0.jar

ENV JAVA_OPTS="-Xmx512m -Xms256m"

EXPOSE 8084

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
