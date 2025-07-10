#!/bin/bash

set -e

# --- Location awareness ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../../.."
cd "$REPO_ROOT"

ENV=${1:-local}
COMPONENT=${2:-all}
NS=${3:-payment}

OVERLAY="$REPO_ROOT/infra/k8s/overlays/$ENV/$COMPONENT"

if [ ! -d "$OVERLAY" ]; then
  echo "❌ Overlay path does not exist: $OVERLAY"
  exit 2
fi

# Optional: Delete any overlay secrets yaml files
if [ -d "$OVERLAY/secrets" ]; then
  for s in "$OVERLAY"/secrets/*.yaml; do
    [ -f "$s" ] && echo "🗑️  Deleting secret: $s" && kubectl delete -f "$s" -n "$NS" --ignore-not-found || true
  done
fi

kubectl delete -k "$OVERLAY" -n "$NS" --ignore-not-found || true

echo ""
echo "🗑️  Deleted: $OVERLAY from namespace $NS"
