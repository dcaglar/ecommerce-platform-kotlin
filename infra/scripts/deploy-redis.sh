#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/redis-values-local.yaml"
# --- Helm Repo Setup ---
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update


# --- Deploy/Upgrade Postgres ---
helm upgrade --install redis bitnami/redis \
  -n payment --create-namespace \
  -f infra/helm-values/redis-values-local.yaml


echo "✅ payment-db deployed with values from: $VALUES_FILE"