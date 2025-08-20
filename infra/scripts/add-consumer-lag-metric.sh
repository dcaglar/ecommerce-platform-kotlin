#!/bin/bash
set -euo pipefail

# Adds (or updates) the prometheus-adapter external metric rule for Kafka consumer group lag.
# Run AFTER:
#   1. Base monitoring deployed (deploy-monitoring-base.sh)
#   2. Kafka + kafka lag exporter running and scraped by Prometheus
#   3. payment-consumers (or any consumer groups) have produced lag metrics
# This performs a helm upgrade of the prometheus-adapter with the external rule.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Allow overriding namespace (defaults to monitoring)
NS=${NS:-monitoring}

# Detect Prometheus service (same logic as base script)
if kubectl -n "$NS" get svc prometheus-operated >/dev/null 2>&1; then
  PROM_SVC=prometheus-operated
elif kubectl -n "$NS" get svc prometheus-stack-kube-prom-prometheus >/dev/null 2>&1; then
  PROM_SVC=prometheus-stack-kube-prom-prometheus
else
  echo "❌ Prometheus service not found in namespace '$NS'" >&2
  kubectl -n "$NS" get svc
  exit 1
fi
PROM_URL="http://${PROM_SVC}.${NS}.svc.cluster.local"
PROM_PORT=9090

echo "▶️  Upserting external metric rule kafka_consumer_group_lag against ${PROM_URL}:${PROM_PORT}"

# OPTIONAL: allow relaxing namespace filter if exporter omits it
REQUIRE_NAMESPACE_LABEL=${REQUIRE_NAMESPACE_LABEL:-true}
if [[ "$REQUIRE_NAMESPACE_LABEL" == "true" ]]; then
  SERIES_QUERY="kafka_consumergroup_lag{namespace!=\"\",consumergroup!=\"\"}"
else
  SERIES_QUERY="kafka_consumergroup_lag{consumergroup!=\"\"}"
fi

echo "ℹ️  Using seriesQuery: $SERIES_QUERY"

echo "▶️  Helm upgrade prometheus-adapter with external rule"
helm upgrade --install prometheus-adapter prometheus-community/prometheus-adapter \
  -n "$NS" \
  --set "prometheus.url=${PROM_URL}" \
  --set "prometheus.port=${PROM_PORT}" \
  --set logLevel=2 \
  -f - <<EOF
rules:
  default: false
  external:
    - seriesQuery: '${SERIES_QUERY}'
      resources:
        overrides:
          namespace:
            resource: namespace
      name:
        as: kafka_consumer_group_lag
      metricsQuery: |
        sum by (namespace, consumergroup) (
          max_over_time(kafka_consumergroup_lag{<<.LabelMatchers>>}[2m])
        )
EOF

echo "⏳ Waiting for prometheus-adapter rollout..."
kubectl -n "$NS" rollout status deploy/prometheus-adapter

echo "🔍 Verifying APIService registration (external.metrics)..."
kubectl get apiservices | grep external.metrics || true

echo "✅ External metric rule applied. Test with:"
echo "kubectl get --raw /apis/external.metrics.k8s.io/v1beta1/namespaces/<app-namespace>/kafka_consumer_group_lag?labelSelector=consumergroup%3D<group> | jq ."

