#!/usr/bin/env bash
set -euo pipefail
# deploy-monitoring-stack-azure.sh
#
# Deploys kube-prometheus-stack onto Azure AKS (centralpool).
# After deployment, enables ServiceMonitors in all azure application values
# so Prometheus starts scraping the payment platform workloads.
#
# Prerequisites:
#   - kubectl context points to the AKS cluster
#   - yq v4+ installed (used to toggle serviceMonitor flags)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/monitoring-stack-values-azure.yaml"

# ── Validate context ────────────────────────────────────────────────────────────
CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "none")
echo "📍 Current kubectl context: $CURRENT_CONTEXT"
if [[ "$CURRENT_CONTEXT" == "orbstack" || "$CURRENT_CONTEXT" == *"minikube"* ]]; then
  echo "❌ Context looks local. This script is for Azure AKS only. Aborting." >&2
  exit 1
fi

# ── Deploy kube-prometheus-stack ────────────────────────────────────────────────
echo "▶️  Deploying kube-prometheus-stack (Azure profile) with: $VALUES_FILE"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update >/dev/null

helm upgrade --install prometheus-stack prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  -f "$VALUES_FILE" \
  --wait --timeout 15m

# ── Wait for Operator and Prometheus ─────────────────────────────────────────
echo "⏳ Waiting for Prometheus Operator..."
kubectl -n monitoring rollout status deploy/prometheus-stack-kube-prom-operator --timeout=5m

echo "⏳ Waiting for Prometheus StatefulSet..."
kubectl -n monitoring rollout status statefulset/prometheus-prometheus-stack-kube-prom-prometheus --timeout=10m

echo "⏳ Waiting for Grafana..."
kubectl -n monitoring rollout status deploy/prometheus-stack-grafana --timeout=5m

kubectl -n monitoring get svc prometheus-stack-kube-prom-prometheus
echo "✅ kube-prometheus-stack is up on Azure."

# ── Enable ServiceMonitors in all azure application values ─────────────────────
echo ""
echo "🔁 Enabling ServiceMonitors in azure application values..."
yq -i '.controller.metrics.serviceMonitor.enabled = true' \
  "$REPO_ROOT/infra/helm-values/ingress-values.yaml"
yq -i '.serviceMonitor.enabled = true' \
  "$REPO_ROOT/infra/helm-values/payment-edge-cell-values-azure.yaml"
yq -i '.serviceMonitor.enabled = true' \
  "$REPO_ROOT/infra/helm-values/payment-consumers-values-azure.yaml"
yq -i '.serviceMonitor.enabled = true' \
  "$REPO_ROOT/infra/helm-values/payment-central-relay-values-azure.yaml"
echo "✅ ServiceMonitors enabled in azure values."

# ── Grafana access info ────────────────────────────────────────────────────────
echo ""
GRAFANA_POD=$(kubectl -n monitoring get pod -l "app.kubernetes.io/name=grafana" \
  -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
if [ -n "$GRAFANA_POD" ]; then
  echo "📊 Grafana access via port-forward:"
  echo "   kubectl -n monitoring port-forward svc/prometheus-stack-grafana 3000:80"
  echo "   Open: http://localhost:3000  |  admin / prom-operator"
fi

echo ""
echo "⚠️  Re-deploy application charts to activate ServiceMonitors:"
echo "   ./infra/scripts/deploy-payment-edge-cell-azure.sh"
echo "   ./infra/scripts/deploy-payment-platform-config-azure.sh"
echo ""
echo "✅ Monitoring fully deployed."
echo "   Prometheus: 15s scrape  |  24h retention  |  50Gi PVC  |  pool: central"
echo "   HPA metric kafka_consumer_group_lag_worst2: LIVE"
