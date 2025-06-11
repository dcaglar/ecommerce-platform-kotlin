#!/bin/bash

# Show status of infra and app containers

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  echo "Usage: $0"
  echo "Shows status of app and infra containers."
  exit 0
fi

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo "❌ Docker is not running. Please start Docker Desktop or the Docker daemon."
  exit 1
fi

echo "\n🔎 Infra containers (docker-compose.infra.yml):"
docker compose -f ../docker-compose.infra.yml ps

echo "\n🔎 App containers (docker-compose.app.yml):"
docker compose -f ../docker-compose.app.yml ps

