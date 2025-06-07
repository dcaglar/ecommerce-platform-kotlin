#!/bin/bash
echo "🛑 Stopping and removing infra containers..."
docker compose -f ../docker-compose.infra.yml down
echo "✅ Infra containers removed."