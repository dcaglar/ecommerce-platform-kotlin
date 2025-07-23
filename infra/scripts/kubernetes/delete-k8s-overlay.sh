#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../../.."
cd "$REPO_ROOT"

ENV=${1:-local}
COMPONENT=${2:-all}
NS=${3:-payment}

OVERLAY="$REPO_ROOT/infra/k8s/overlays/$ENV/$COMPONENT"

if [[ ! -d "$OVERLAY" ]]; then
  echo "❌ Overlay path does not exist: $OVERLAY"; exit 2
fi

# --- delete overlay-specific Secrets first ---
if [[ -d "$OVERLAY/secrets" ]]; then
  for s in "$OVERLAY"/secrets/*.yaml; do
    [[ -f "$s" ]] && kubectl delete -f "$s" -n "$NS" --ignore-not-found || true
  done
fi

echo "🗑️  Deleting component '$COMPONENT' ..."



if [[ "$COMPONENT" == "monitoring" || "$COMPONENT" == "all" ]]; then
  kubectl delete -k "$OVERLAY"              # ← let each YAML set its own ns
else
  kubectl delete -k "$OVERLAY" -n "$NS"
fi

echo "✅  Deleted $COMPONENT from overlay $ENV"

# --- PVC cleanup (namespace-scoped) ---
kubectl delete pvc -n "$NS" data-kafka-0 data-payment-db-0 data-zookeeper-0 --ignore-not-found || true

# kill any stray port-forwards
pkill -f "kubectl port-forward" || true
echo "✅  Killed all port-forwards."