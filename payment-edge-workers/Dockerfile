# syntax=docker/dockerfile:1.4

FROM maven:3.9.6-eclipse-temurin-21-jammy AS builder
WORKDIR /app

# Copy root and all module pom.xml files for dependency resolution
COPY pom.xml ./
COPY common/pom.xml common/
COPY payment-domain/pom.xml payment-domain/
COPY common-test/pom.xml common-test/
COPY payment-application/pom.xml payment-application/
COPY payment-infrastructure/pom.xml payment-infrastructure/
COPY payment-service/pom.xml payment-service/
COPY payment-consumers/pom.xml payment-consumers/
COPY payment-edge-workers/pom.xml payment-edge-workers/
COPY payment-central-relay/pom.xml payment-central-relay/


# Warm the dependency cache
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

# Copy all module source directories (required by parent pom.xml)
COPY common/ common/
COPY payment-domain/ payment-domain/
COPY common-test/ common-test/
COPY payment-application/ payment-application/
COPY payment-infrastructure/ payment-infrastructure/
COPY payment-edge-workers/ payment-edge-workers/


# Build the payment-service jar
RUN --mount=type=cache,target=/root/.m2 mvn -pl payment-edge-workers -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /app/payment-edge-workers/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
