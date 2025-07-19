i#!/bin/bash
set -e

NS=${1:-payment}

# --- Location awareness ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../../.."
PVC_BOOTSTRAP="$REPO_ROOT/infra/k8s/bootstrap-pvc"
echo "SCRIPT DIR: $SCRIPT_DIR"
REPO_ROOT="$SCRIPT_DIR/../../.."
echo "REPO DIR: $REPO_ROOT"
cd "$REPO_ROOT"

echo "🚀 Bootstrapping persistent PVCs in namespace $NS..."
if kubectl get ns "$NS" >/dev/null 2>&1; then
echo "ℹ️  Namespace '$NS' already exists."
else
echo "🆕 Creating namespace '$NS'..."
kubectl create ns "$NS"
fi

kubectl apply -k "$PVC_BOOTSTRAP" -n "$NS"

echo "✅ Bootstrapped persistent PVCs in namespace $NS"