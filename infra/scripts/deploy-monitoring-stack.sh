#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/monitoring-stack-values-local.yaml"

echo "▶️  Deploying kube-prometheus-stack with values: $VALUES_FILE"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo update >/dev/null

helm upgrade --install prometheus-stack prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace -f "$VALUES_FILE" \
  --wait --timeout 10m

echo "⏳ Waiting for Prometheus Operator..."
kubectl -n monitoring rollout status deploy/prometheus-stack-kube-prom-operator --timeout=5m

# Wait for Prometheus StatefulSet (name is stable for this chart)
echo "⏳ Waiting for Prometheus StatefulSet..."
kubectl -n monitoring rollout status statefulset/prometheus-prometheus-stack-kube-prom-prometheus --timeout=10m

# Sanity: show the Prometheus service we will use for the adapter later
kubectl -n monitoring get svc prometheus-stack-kube-prom-prometheus
echo "✅ kube-prometheus-stack is up."