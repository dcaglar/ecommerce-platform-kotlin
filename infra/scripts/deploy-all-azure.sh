#!/usr/bin/env bash
set -euo pipefail

NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"


echo "🚀 Deploying platform config and secrets..."
"$SCRIPT_DIR/deploy-payment-platform-config-azure.sh"

echo "🚀 Deploying  central databases..."
"$SCRIPT_DIR/deploy-central-db-azure.sh"

echo "🚀 Deploying redis..."
"$SCRIPT_DIR/deploy-redis-azure.sh"

echo "🚀 Deploying yugabytedb..."
"$SCRIPT_DIR/deploy-yugabyte-azure.sh"

echo "🚀 Deploying kafka..."
"$SCRIPT_DIR/deploy-kafka-azure.sh"

echo "✅ All components deployed!"
