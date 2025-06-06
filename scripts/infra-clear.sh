#!/bin/bash

echo "🧹 Stopping and removing containers..."
docker compose down --volumes --remove-orphans

echo "🧼 Pruning unused Docker resources..."
docker system prune --volumes --force

echo "✅ Docker cleanup complete."