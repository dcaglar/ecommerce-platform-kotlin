#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"
CHART_ROOT="$REPO_ROOT/charts/payment-edge-cell"


helm secrets upgrade --install payment-edge-cell "$CHART_ROOT" \
  -n payment --create-namespace \
  -f "$CHART_ROOT/values.yaml" \
  -f "$CHART_ROOT/local/values.yaml" \
  -f "secrets://$REPO_ROOT/edge-cell-sops-secrets.yaml"


 # Removed: rollout status calls.
 # Readiness is now guaranteed by the pg_isready initContainer inside the manifest (Rule C).