#!/bin/bash
set -euo pipefail

# --- Location Aware ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/monitoring-stack-values-local.yaml"
# --- Deploy/Upgrade Postgres ---
 helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
 helm repo update
 helm upgrade --install prometheus-stack prometheus-community/kube-prometheus-stack \
   --namespace payment --create-namespace -f "$VALUES_FILE"


echo "✅ prom sstaack deployed with values from: $VALUES_FILE"