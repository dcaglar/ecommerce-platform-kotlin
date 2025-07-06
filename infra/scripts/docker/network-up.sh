#!/bin/bash

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  echo "Usage: $0"
  echo "Creates required external Docker networks if not already present."
  exit 0
fi

EXTERNAL_NETWORKS=("payment-net" "messaging-net" "monitoring-net" "auth-net")

echo "🔧 Creating external Docker networks (if not already present)..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo "❌ Docker is not running. Please start Docker Desktop or the Docker daemon."
  exit 1.
fi

for network in "${EXTERNAL_NETWORKS[@]}"; do
  docker network inspect "$network" >/dev/null 2>&1 || docker network create "$network"
done

echo "✅ Networks are ready. Current Docker networks:"
docker network ls
