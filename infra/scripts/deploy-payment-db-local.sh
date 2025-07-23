#!/bin/bash
set -euo pipefail

# --- Location Aware ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/payment-db-values-local.yaml"
# --- Helm Repo Setup ---
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update


# --- Deploy/Upgrade Postgres ---
helm upgrade --install payment-db bitnami/postgresql \
  -n payment --create-namespace \
  --version 15.5.1 \
  -f "$VALUES_FILE"


echo "✅ payment-db deployed with values from: $VALUES_FILE"