#!/bin/bash
set -e

EXTERNAL_NETWORKS=("payment-net" "messaging-net")

echo "🔧 Creating external Docker networks (if not already present)..."
for network in "${EXTERNAL_NETWORKS[@]}"; do
  docker network inspect "$network" >/dev/null 2>&1 || docker network create "$network"
done
echo "✅ Networks are ready."
