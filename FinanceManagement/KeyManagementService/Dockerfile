# --- Stage 1: Build ---
FROM gradle:8.5-jdk21 AS build
#Gradle hỗ trợ Java 21

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src ./src

# Sử dụng cache nếu có thể
RUN gradle build -x test --no-daemon

# --- Stage 2: Run ---
# Must use glibc-based image (not Alpine/musl) because gRPC's bundled
# Netty tcnative native SSL library is compiled against glibc
FROM eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# Download OpenTelemetry Java agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/libs/opentelemetry-javaagent-2.11.0.jar

ENV JAVA_OPTS="-Xmx512m -Xms256m"

EXPOSE 8084

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]