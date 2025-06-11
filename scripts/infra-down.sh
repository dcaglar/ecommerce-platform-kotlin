#!/bin/bash

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  echo "Usage: $0"
  echo "Stops and removes the infra containers (preserving volumes)."
  exit 0
fi

echo "🛑 Stopping infra containers (preserving volumes)..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo "❌ Docker is not running. Please start Docker Desktop or the Docker daemon."
  exit 1
fi

docker compose -f ../docker-compose.infra.yml down
STATUS=$?
if [ $STATUS -eq 0 ]; then
  echo "✅ Infra stopped."
  docker compose -f ../docker-compose.infra.yml ps
else
  echo "❌ Failed to stop infra containers."
fi
