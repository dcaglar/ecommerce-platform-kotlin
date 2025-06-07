#!/bin/bash
echo "🛑 Stopping infra containers (preserving volumes)..."
docker compose -f ../docker-compose.infra.yml down
echo "✅ Infra stopped."
