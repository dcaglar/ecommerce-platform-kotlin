# syntax=docker/dockerfile:1.4

FROM maven:3.9.6-eclipse-temurin-21-jammy AS builder
WORKDIR /app

# Copy root and all module pom.xml files for dependency resolution
COPY pom.xml ./
COPY common/pom.xml common/
COPY payment-domain/pom.xml payment-domain/
COPY payment-application/pom.xml payment-application/
COPY common-test/pom.xml common-test/
COPY payment-infrastructure/pom.xml payment-infrastructure/
COPY payment-service/pom.xml payment-service/
COPY payment-consumers/pom.xml payment-consumers/
# Warm the dependency cache
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
# Copy all module source directories (required by parent pom.xml)
COPY common/ common/
COPY payment-domain/ payment-domain/
COPY common-test/ common-test/
COPY payment-application/ payment-application/
COPY payment-infrastructure/ payment-infrastructure/
COPY payment-service/ payment-service/
# Do NOT copy payment-consumers/

# Build the payment-service jar
RUN --mount=type=cache,target=/root/.m2 mvn -pl payment-service -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app


COPY --from=builder /app/payment-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
