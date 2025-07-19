#!/bin/bash

set -e

# --- Location awareness ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "SCRIPT DIR: $SCRIPT_DIR"
REPO_ROOT="$SCRIPT_DIR/../../.."
echo "REPO DIR: $REPO_ROOT"
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

kubectl delete -k "$OVERLAY" -n "$NS"  || true

echo "🗑️  Deleted: $OVERLAY from namespace $NS"


#echo "🧹 Deleting kafka PVCs in namespace"
kubectl delete pvc -n payment data-kafka-0  || true
kubectl delete pvc -n payment data-payment-db-0  || true
kubectl delete pvc -n payment data-zookeeper-0   || true


#kubectl delete pvc -n payment  keycloak-pg-pvc || true
#kubectl delete pvc -n payment redis-data-pvc || true
#
#echo "✅  Deleted all kafka pvc"

echo "killing port-forwards..."

pkill -f "kubectl port-forward" || true

echo "✅  Killed all port-forwards."