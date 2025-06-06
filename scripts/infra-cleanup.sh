#!/bin/bash

echo "🧹 Stopping and removing infrastructure containers, networks, and volumes..."
docker compose -f docker-compose.infra.yml down --volumes --remove-orphans

echo "✅ Infra cleanup complete."