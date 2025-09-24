#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/redis-values-local.yaml"

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm upgrade --install redis bitnami/redis \
  -n payment --create-namespace \
  -f "$VALUES_FILE"

echo "✅ redis deployed"