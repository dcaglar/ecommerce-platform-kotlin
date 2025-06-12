#!/bin/bash

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  echo "Usage: $0"
  echo "Starts the infra containers using docker-compose."
  exit 0
fi

echo "📦 Starting infra containers..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo "❌ Docker is not running. Please start Docker Desktop or the Docker daemon."
  exit 1
fi

docker compose -f ../docker-compose.infra.yml up -d
STATUS=$?
if [ $STATUS -eq 0 ]; then
  echo "✅ Infra is up."
  docker compose -f ../docker-compose.infra.yml ps
else
  echo "❌ Failed to start infra containers."
fi
