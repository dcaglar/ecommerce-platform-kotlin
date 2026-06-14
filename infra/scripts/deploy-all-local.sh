#!/usr/bin/env bash
set -euo pipefail

NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

echo "🛡️  Checking Kubernetes context..."
CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "none")
if [[ "$CURRENT_CONTEXT" != "orbstack" ]]; then
  echo "⚠️  Current context is '$CURRENT_CONTEXT', but this script requires 'orbstack'."
  if kubectl config get-contexts orbstack >/dev/null 2>&1; then
    echo "🔄 Switching context to 'orbstack'..."
    kubectl config use-context orbstack
  else
    echo "❌ OrbStack context not found! Is OrbStack running with Kubernetes enabled?"
    exit 1
  fi
fi



echo "🚀 Deploying platform config and secrets..."
"$SCRIPT_DIR/deploy-payment-platform-config-local.sh"


echo "🚀 Deploying keycloak"
"$SCRIPT_DIR/deploy-keycloak-local.sh"


echo "🚀 Deploying  central databases..."
"$SCRIPT_DIR/deploy-central-db-local.sh"

echo "🚀 Deploying redis..."
"$SCRIPT_DIR/deploy-redis-local.sh"

echo "🚀 Deploying yugabytedb..."
"$SCRIPT_DIR/deploy-yugabyte-local.sh"

echo "🚀 Deploying kafka..."
"$SCRIPT_DIR/deploy-kafka-local.sh"

echo "🚀 Deploying KEDA (Autoscaler)..."
"$SCRIPT_DIR/deploy-keda-local.sh"

echo "✅ All components deployed!"
