#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"
CHART_ROOT="$REPO_ROOT/charts/payment-central-relay"

helm  secrets upgrade --install payment-central-relay "$CHART_ROOT" \
  -n payment --create-namespace \
  -f "$CHART_ROOT/values.yaml" \
  -f "$CHART_ROOT/local/values.yaml"
  -f "secrets://$REPO_ROOT/edge-db-sops-secrets.yaml"
  -f "secrets://$REPO_ROOT/central-db-sops-secrets.yaml"