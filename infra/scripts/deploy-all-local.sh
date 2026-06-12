#!/usr/bin/env bash
set -euo pipefail

NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"


echo "🚀 Deploying secrets..."
"$SCRIPT_DIR/deploy-secrets.sh"

echo "🚀 Deploying  central databases..."
"$SCRIPT_DIR/deploy-central-db-local.sh"

echo "🚀 Deploying redis..."
"$SCRIPT_DIR/deploy-redis-local.sh"

echo "🚀 Deploying yugabytedb..."
"$SCRIPT_DIR/deploy-yugabyte-local.sh"

echo "🚀 Deploying kafka..."
"$SCRIPT_DIR/deploy-kafka-local.sh"

echo "✅ All components deployed!"
