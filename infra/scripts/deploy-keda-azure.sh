#!/usr/bin/env bash
set -euo pipefail

CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "none")
echo "📍 Current kubectl context: $CURRENT_CONTEXT"
if [[ "$CURRENT_CONTEXT" == "orbstack" || "$CURRENT_CONTEXT" == *"minikube"* ]]; then
  echo "❌ Context looks local. This script is for Azure AKS only. Aborting." >&2
  exit 1
fi

echo "▶️  Deploying KEDA (Kubernetes Event-driven Autoscaling) on Azure"
helm repo add kedacore https://kedacore.github.io/charts >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true

# Install KEDA into the keda namespace, pinning it to the centralpool
helm upgrade --install keda kedacore/keda \
  --namespace keda --create-namespace \
  --set nodeSelector.pool=central \
  --wait --timeout 5m

echo "✅ KEDA deployed successfully on Azure (centralpool)."
