#!/bin/bash
echo "📦 Starting infra containers..."
docker compose -f ../docker-compose.infra.yml up -d
echo "✅ Infra is up."
