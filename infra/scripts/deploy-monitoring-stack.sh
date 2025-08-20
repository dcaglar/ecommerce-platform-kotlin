#!/bin/bash
set -euo pipefail

# Deploy ONLY the monitoring stack (Prometheus Operator + Prometheus + Alertmanager + Grafana)
# and install a baseline prometheus-adapter WITHOUT the kafka consumer lag external metric rule.
# Later, after payment-consumers (and the kafka lag exporter) are deployed, run
#   ./infra/scripts/add-consumer-lag-metric.sh
# to add the external metric rule for HPA.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/monitoring-stack-values-local.yaml"

echo "▶️  Deploying kube-prometheus-stack (base) with values: $VALUES_FILE"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update >/dev/null
helm upgrade --install prometheus-stack prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace -f "$VALUES_FILE"

echo "⏳ Waiting for Prometheus Operator rollout..."
kubectl -n monitoring rollout status deploy/prometheus-stack-kube-prom-operator

# Detect Prometheus StatefulSet
PROM_STS="$(kubectl -n monitoring get sts -l app.kubernetes.io/name=prometheus -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
if [[ -z "${PROM_STS:-}" ]]; then
  PROM_STS="$(kubectl -n monitoring get sts --no-headers | awk '/^prometheus-/{print $1; exit}')"
fi
if [[ -n "${PROM_STS:-}" ]]; then
  echo "⏳ Waiting for Prometheus StatefulSet: $PROM_STS"
  kubectl -n monitoring rollout status statefulset/"$PROM_STS"
else
  echo "⚠️  Could not auto-detect Prometheus StatefulSet (continuing)."
fi

# Choose Prometheus service
if kubectl -n monitoring get svc prometheus-operated >/dev/null 2>&1; then
  PROM_SVC=prometheus-operated
elif kubectl -n monitoring get svc prometheus-stack-kube-prom-prometheus >/dev/null 2>&1; then
  PROM_SVC=prometheus-stack-kube-prom-prometheus
else
  echo "❌ Prometheus service not found in monitoring namespace" >&2
  kubectl -n monitoring get svc
  exit 1
fi
PROM_URL="http://${PROM_SVC}.monitoring.svc.cluster.local"
PROM_PORT=9090
echo "✅ Using Prometheus at: ${PROM_URL}:${PROM_PORT}"

echo "▶️  Installing baseline prometheus-adapter (no external rules yet)"
helm upgrade --install prometheus-adapter prometheus-community/prometheus-adapter \
  -n monitoring \
  --set "prometheus.url=${PROM_URL}" \
  --set "prometheus.port=${PROM_PORT}" \
  --set logLevel=2 \
  -f - <<'EOF'
rules:
  default: false
  # Intentionally no 'external' section yet; added later once exporters + consumers exist
EOF

kubectl -n monitoring rollout status deploy/prometheus-adapter

echo "ℹ️  Base monitoring stack deployed. Next steps:"
echo "  1. Deploy Kafka + kafka lag exporter (ensure ServiceMonitor present)."
echo "  2. Deploy payment-consumers (they create the consumergroup)."
echo "  3. Run ./infra/scripts/add-consumer-lag-metric.sh to add external metric rule."

