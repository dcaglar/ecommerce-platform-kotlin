#!/usr/bin/env bash
set -euo pipefail

NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

echo "🚀 Deploying redis..."
"$SCRIPT_DIR/deploy-redis-azure.sh"

echo "🚀 Deploying kafka..."
"$SCRIPT_DIR/deploy-kafka-azure.sh"

echo "🚀 Deploying KEDA (Autoscaler)..."
"$SCRIPT_DIR/deploy-keda-azure.sh"

echo "🚀 Deploying monitoring stack (Prometheus + Grafana)..."
"$SCRIPT_DIR/deploy-monitoring-stack-azure.sh"

echo "✅ All components deployed!"
