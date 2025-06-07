#!/bin/bash
echo "🧹 Stopping and removing infra containers, volumes, and orphans..."
docker compose -f ../../docker-compose.infra.yml down --volumes --remove-orphans
echo "✅ Infra cleanup complete."
s