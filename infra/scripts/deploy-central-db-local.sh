#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

helm upgrade --install central-db "$REPO_ROOT/charts/central-db" \
  -n payment --create-namespace \
  -f "$REPO_ROOT/charts/central-db/values.yaml" \
  -f "$REPO_ROOT/charts/central-db/local/values.yaml"

echo "✅ central-db deployed (Local configuration)"