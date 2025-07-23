#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "$SCRIPT_DIR"
REPO_ROOT="$SCRIPT_DIR/../.."

echo "$REPO_ROOT"
cd "$REPO_ROOT"

SECRETS_FILE="$REPO_ROOT/infra/secrets/payment-platform-config-secrets-local.yaml"
VALUES_FILE="$REPO_ROOT/infra/helm-values/payment-platform-config-values-local.yaml"


helm secrets upgrade --install payment-platform-config "$REPO_ROOT/charts/payment-platform-config" \
  -n payment --create-namespace \
  -f "$SECRETS_FILE" \
  -f "$VALUES_FILE"