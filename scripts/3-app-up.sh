#!/bin/bash

echo "📦 Building app image (no cache)..."
docker compose -f docker-compose.app.yml build --no-cache

echo "🚀 Starting app containers..."
docker compose -f docker-compose.app.yml up