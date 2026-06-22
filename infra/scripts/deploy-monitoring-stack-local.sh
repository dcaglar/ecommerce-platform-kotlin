#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/monitoring-stack-values-local.yaml"

echo "▶️  Deploying kube-prometheus-stack with values: $VALUES_FILE"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm upgrade --install prometheus-stack prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace -f "$VALUES_FILE"

echo "✅ kube-prometheus-stack deployment requested."

echo "🚀 Toggling ServiceMonitors to 'true' in application Helm values..."
yq -i '.controller.metrics.serviceMonitor.enabled = true' "$REPO_ROOT/infra/helm-values/ingress-controller-values-local.yaml" || true
yq -i '.serviceMonitor.enabled = true' "$REPO_ROOT/charts/payment-edge-cell/local/values.yaml" || true
yq -i '.serviceMonitor.enabled = true' "$REPO_ROOT/charts/payment-consumers/local/values.yaml" || true
yq -i '.serviceMonitor.enabled = true' "$REPO_ROOT/charts/payment-central-relay/local/values.yaml" || true
yq -i '.serviceMonitor.enabled = true' "$REPO_ROOT/charts/payment-edge-workers/values.yaml" || true
echo "✅ Monitoring switched ON! Applications will now deploy with metrics enabled."