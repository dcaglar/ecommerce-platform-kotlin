#!/bin/bash
set -euo pipefail

# Adds/updates Prometheus Adapter external metric rule for Kafka consumer lag.
# Run AFTER:
#  1) deploy-monitoring.sh (kube-prometheus-stack up)
#  2) Kafka exporter is running and scraped by Prometheus
#  3) your consumer(s) have produced lag metrics

# --- Config / discovery ---
NS=${NS:-monitoring}

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

echo "▶️  Upserting external metric rule 'kafka_consumer_group_lag' against ${PROM_URL}:${PROM_PORT}"

# If your exporter omits the 'namespace' label, set REQUIRE_NAMESPACE_LABEL=false
REQUIRE_NAMESPACE_LABEL=${REQUIRE_NAMESPACE_LABEL:-true}
if [[ "$REQUIRE_NAMESPACE_LABEL" == "true" ]]; then
  SERIES_QUERY="kafka_consumergroup_lag{namespace!=\"\",consumergroup!=\"\"}"
else
  SERIES_QUERY="kafka_consumergroup_lag{consumergroup!=\"\"}"
fi
echo "ℹ️  Using seriesQuery: $SERIES_QUERY"

# --- Install/upgrade Prometheus Adapter with our external rule ---
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

# --- Waits that avoid 503 / FailedDiscoveryCheck races ---
echo "⏳ Waiting for prometheus-adapter rollout..."
kubectl -n "$NS" rollout status deploy/prometheus-adapter

# Wait for APIService to be Available=True
echo "⏳ Waiting for external.metrics.k8s.io APIService to become Available..."
for i in {1..60}; do
  cond=$(kubectl get apiservice v1beta1.external.metrics.k8s.io \
           -o jsonpath='{.status.conditions[?(@.type=="Available")].status}' 2>/dev/null || true)
  if [[ "$cond" == "True" ]]; then
    echo "✅ APIService external.metrics.k8s.io is Available."
    break
  fi
  sleep 2
done

# --- Sanity checks / sample query (optional) ---
echo "🔍 APIService registration:"
kubectl get apiservices | grep external.metrics || true

echo "🔍 ServiceMonitors in 'payment' with release=prometheus-stack:"
kubectl -n payment get servicemonitors -l release=prometheus-stack || true

APP_NS=${APP_NS:-payment}
GROUP=${GROUP:-payment-order-created-consumer-group}
METRIC_PATH="/apis/external.metrics.k8s.io/v1beta1/namespaces/${APP_NS}/kafka_consumer_group_lag?labelSelector=consumergroup%3D${GROUP}"

echo "🔎 Test external metric for consumergroup=${GROUP}: ${METRIC_PATH}"
set +e
kubectl get --raw "${METRIC_PATH}" | jq .
rc=$?
set -e
if [[ $rc -ne 0 ]]; then
  echo "⚠️ External metric query failed. Common causes:"
  echo "   - Adapter not yet Available (re-run in a few seconds)"
  echo "   - Exporter not scraping / Prometheus not seeing series"
  echo "   - Label mismatch (set REQUIRE_NAMESPACE_LABEL=false if needed)"
fi

echo "✅ Done."