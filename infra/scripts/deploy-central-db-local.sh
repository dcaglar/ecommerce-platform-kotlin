#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"
CHART_ROOT="$REPO_ROOT/charts/central-db"


helm dependency update "$CHART_ROOT"

helm secrets upgrade --install central-db "$CHART_ROOT" \
  -n payment --create-namespace \
  -f "$CHART_ROOT/values.yaml" \
  -f "$CHART_ROOT/local/values.yaml" \
  -f "secrets://$REPO_ROOT/central-db-sops-secrets.yaml"

# Clean up the downloaded .tgz dependencies so they don't pollute the IDE/codebase
rm -rf "$CHART_ROOT/charts"
rm -f "$CHART_ROOT/Chart.lock"

  echo "✅ central-db deployed (Local configuration)"
