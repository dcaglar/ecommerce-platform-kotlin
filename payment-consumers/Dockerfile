# syntax=docker/dockerfile:1.4

FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy root and all module pom.xml files for dependency resolution
COPY pom.xml ./
COPY common/pom.xml common/
COPY payment-domain/pom.xml payment-domain/
COPY payment-application/pom.xml payment-application/
COPY payment-infrastructure/pom.xml payment-infrastructure/
COPY payment-service/pom.xml payment-service/
COPY payment-consumers/pom.xml payment-consumers/

# Copy all module source directories (required by parent pom.xml)
COPY common/ common/
COPY payment-domain/ payment-domain/
COPY payment-application/ payment-application/
COPY payment-infrastructure/ payment-infrastructure/
COPY payment-service/ payment-service/
COPY payment-consumers/ payment-consumers/

# Download all dependencies for payment-consumers and its dependencies
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline -pl payment-consumers --also-make

# Build only payment-consumers and its dependencies
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B clean package -DskipTests -pl payment-consumers --also-make

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

RUN apk add --no-cache wget
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/payment-consumers/target/payment-consumers-*.jar app.jar
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --spider --quiet http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=80", "-jar", "app.jar"]
