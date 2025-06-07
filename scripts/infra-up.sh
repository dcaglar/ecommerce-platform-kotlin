#!/bin/bash
echo "🚀 Starting infrastructure services..."
docker compose -f ../docker-compose.infra.yml up -d
echo "✅ Infra is up."
