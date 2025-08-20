#!/bin/bash
set -euo pipefail

# --- Location Aware ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"



VALUES_FILE="$REPO_ROOT/infra/helm-values/monitoring-stack-values-local.yaml"
# --- Deploy/Upgrade Postgres ---
 helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
 helm repo update
 helm upgrade --install prometheus-stack prometheus-community/kube-prometheus-stack \
   --namespace monitoring --create-namespace -f "$VALUES_FILE"



echo "▶️  Deploying kube-prometheus-stack with values: $VALUES_FILE"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo update >/dev/null
helm upgrade --install prometheus-stack prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace -f "$VALUES_FILE"

echo "⏳ Waiting for Prometheus Operator..."
kubectl -n monitoring rollout status deploy/prometheus-stack-kube-prom-operator

# --- Wait for Prometheus to be ready (detect the StatefulSet name) ---
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

# --- Discover Prometheus service (prefer the operated service) ---
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

# --- Install Prometheus Adapter (EXTERNAL metrics only) ---
echo "▶️  Installing/Upgrading Prometheus Adapter (external metrics API)"
# (Uninstall is optional; upgrade handles changes)
# helm -n monitoring uninstall prometheus-adapter || true
helm upgrade --install prometheus-adapter prometheus-community/prometheus-adapter \
  -n monitoring \
  --set "prometheus.url=${PROM_URL}" \
  --set "prometheus.port=${PROM_PORT}" \
  --set logLevel=4 \
  -f - <<'EOF'
rules:
  default: false
  external:
    - seriesQuery: 'kafka_consumergroup_lag{namespace!="", consumergroup!=""}'
      resources:
        overrides:
          namespace:
            resource: "namespace"
      name:
        as: "kafka_consumer_group_lag"
      metricsQuery: |
        sum by (namespace, consumergroup) (
          max_over_time(kafka_consumergroup_lag{<<.LabelMatchers>>}[2m])
        )
EOF

kubectl -n monitoring rollout status deploy/prometheus-adapter

# Show the effective adapter config (for sanity)
kubectl -n monitoring get cm prometheus-adapter -o jsonpath='{.data.config\.yaml}' | sed -n '1,120p' || true

# --- Sanity checks ---
echo "🔎 External Metrics API registered:"
kubectl get apiservices | grep external.metrics || true

echo "🔎 ServiceMonitors with release=prometheus-stack in 'payment' (exporters should appear):"
kubectl -n payment get servicemonitors -l release=prometheus-stack || true

# Sample external metric query for your consumer group (correct path; no /metrics/)
GROUP="payment-order-created-consumer-group"
METRIC_PATH="/apis/external.metrics.k8s.io/v1beta1/namespaces/payment/kafka_consumer_group_lag?labelSelector=consumergroup%3D${GROUP}"
echo "🔎 External metric sample for consumergroup=${GROUP}: ${METRIC_PATH}"
kubectl get --raw "${METRIC_PATH}" | jq .