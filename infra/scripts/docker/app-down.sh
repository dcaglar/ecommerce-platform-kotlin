#!/bin/bash
set -e

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  echo "Usage: $0"
  echo "Stops and removes the app containers."
  exit 0
fi

echo "🛑 Stopping app containers..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo "❌ Docker is not running. Please start Docker Desktop or the Docker daemon."
  exit 1
fi

docker compose -f docker-compose.app.yml down
STATUS=$?
if [ $STATUS -eq 0 ]; then
  echo "✅ App containers stopped."
  docker compose -f docker-compose.app.yml ps
else
  echo "❌ Failed to stop app containers."
fi
