#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."

cd "$REPO_ROOT"
helm upgrade --install payment-central-relay "$REPO_ROOT/charts/payment-central-relay" \
  -n payment --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/payment-central-relay-azure.yaml"
