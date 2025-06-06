#!/bin/bash

echo "🧪 Step 1: Cleaning and packaging Spring Boot project..."
mvn clean package -DskipTests

echo "🐳 Step 2: Building Docker image for payment-service (no cache)..."
docker build --no-cache -t ecommerce-platform-kotlin-payment-service ./payment-service

echo "🔌 Step 3: Starting all containers via Docker Compose..."
docker compose up --build