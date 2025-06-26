# syntax=docker/dockerfile:1.4

FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml ./
COPY payment-service/pom.xml payment-service/
COPY common/pom.xml common/

# Download all dependencies for all modules
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

COPY . .

# Build all modules (including common) and package payment-service
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Ensure wget is present for healthcheck
RUN apk add --no-cache wget

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/payment-service/target/payment-service-*.jar app.jar


EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --spider --quiet http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=80", "-jar", "app.jar"]