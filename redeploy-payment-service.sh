#!/bin/bash

# 🧹 Stop and remove just the payment-service container
echo "🛑 Stopping payment-service..."
docker compose -f docker-compose.app.yml stop payment-service

echo "🗑 Removing payment-service..."
docker compose -f docker-compose.app.yml rm -f payment-service

# 🛠️ Rebuild and restart just payment-service (without --no-cache)
echo "📦 Rebuilding payment-service..."
docker compose -f docker-compose.app.yml build payment-service

echo "🚀 Starting payment-service..."
docker compose -f docker-compose.app.yml up -d payment-service

# 📋 Optional: Tail logs
echo "📄 Tailing logs..."
docker logs -f payment-service