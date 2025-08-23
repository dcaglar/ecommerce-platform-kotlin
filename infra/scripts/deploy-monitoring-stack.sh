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
  -n monitoring --create-namespace -f "$VALUES_FILE"

echo "⏳ Waiting for Prometheus Operator..."
kubectl -n monitoring rollout status deploy/prometheus-stack-kube-prom-operator

# Wait for Prometheus StatefulSet
PROM_STS="$(kubectl -n monitoring get sts -l app.kubernetes.io/name=prometheus -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
if [[ -z "${PROM_STS:-}" ]]; then
  PROM_STS="$(kubectl -n monitoring get sts --no-headers | awk '/^prometheus-/{print $1; exit}')"
fi
if [[ -n "${PROM_STS:-}" ]]; then
  echo "⏳ Waiting for Prometheus StatefulSet: $PROM_STS"
  kubectl -n monitoring rollout status statefulset/"$PROM_STS"
else
  echo "⚠️ Could not detect Prometheus StatefulSet; continuing."
fi

# Discover Prometheus service (optional sanity)
if kubectl -n monitoring get svc prometheus-operated >/dev/null 2>&1; then
  PROM_SVC=prometheus-operated
elif kubectl -n monitoring get svc prometheus-stack-kube-prom-prometheus >/dev/null 2>&1; then
  PROM_SVC=prometheus-stack-kube-prom-prometheus
else
  echo "❌ Could not find a Prometheus service in 'monitoring' ns." >&2
  kubectl -n monitoring get svc
  exit 1
fi
PROM_URL="http://${PROM_SVC}.monitoring.svc.cluster.local"
PROM_PORT=9090
echo "✅ Using Prometheus at: ${PROM_URL}:${PROM_PORT}"

# (No Prometheus Adapter install here; run consumer-lag-metrics.sh afterwards)
echo "ℹ️  Now run: ./infra/scripts/consumer-lag-metrics.sh"