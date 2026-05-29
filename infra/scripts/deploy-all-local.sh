#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"



echo "🚀 Deploying payment configmap..."
"$SCRIPT_DIR/deploy-payment-platform-config.sh"

echo "🚀 Deploying keycloak..."
"$SCRIPT_DIR/deploy-keycloak-local.sh"



echo "🚀 Deploying  central databases..."
"$SCRIPT_DIR/deploy-central-db-local.sh"

echo "🚀 Deploying redis..."
"$SCRIPT_DIR/deploy-redis-local.sh"

echo "🚀 Deploying kafka..."
"$SCRIPT_DIR/deploy-kafka-local.sh"




echo "✅ All components deployed!"

