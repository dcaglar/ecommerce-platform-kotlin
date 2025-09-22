#!/usr/bin/env bash
set -euo pipefail

# Purpose: Install/upgrade Prometheus Adapter to expose Kafka consumer lag
#          as an External Metric (for HPA). Run AFTER:
#            1) kube-prometheus-stack is up
#            2) kafka-exporter is scraped by Prometheus
#            3) your consumer has created lag time-series

# -------- Config --------
NS=${NS:-monitoring}
APP_NS=${APP_NS:-payment}
GROUP=${GROUP:-payment-order-psp-call-executor-consumer-group}
EXTERNAL_METRIC_NAME=${EXTERNAL_METRIC_NAME:-kafka_consumer_group_lag}
REQUIRE_NAMESPACE_LABEL=${REQUIRE_NAMESPACE_LABEL:-true}
METRICS_RELIST_INTERVAL=${METRICS_RELIST_INTERVAL:-30s}

# modest caps so adapter won't be <none>
ADAPTER_REQ_CPU=${ADAPTER_REQ_CPU:-20m}
ADAPTER_LIM_CPU=${ADAPTER_LIM_CPU:-100m}
ADAPTER_REQ_MEM=${ADAPTER_REQ_MEM:-32Mi}
ADAPTER_LIM_MEM=${ADAPTER_LIM_MEM:-96Mi}

echo "▶️  Installing external metric '${EXTERNAL_METRIC_NAME}' for consumergroup='${GROUP}' in ns='${APP_NS}'"

# -------- Discover Prometheus service --------
PROM_SVC=""
if kubectl -n "$NS" get svc prometheus-operated >/dev/null 2>&1; then
  PROM_SVC=prometheus-operated
elif kubectl -n "$NS" get svc prometheus-prometheus-stack-kube-prom-prometheus >/dev/null 2>&1; then
  PROM_SVC=prometheus-prometheus-stack-kube-prom-prometheus
elif kubectl -n "$NS" get svc prometheus-stack-kube-prom-prometheus >/dev/null 2>&1; then
  # some older chart names
  PROM_SVC=prometheus-stack-kube-prom-prometheus
else
  echo "❌ Prometheus service not found in namespace '$NS'" >&2
  kubectl -n "$NS" get svc
  exit 1
fi
PROM_URL="http://${PROM_SVC}.${NS}.svc.cluster.local"
PROM_PORT=9090
echo "ℹ️  Using Prometheus at ${PROM_URL}:${PROM_PORT}"

# -------- Prevent accidental duplicate adapters --------
if kubectl -n "$NS" get deploy/prometheus-adapter >/dev/null 2>&1; then
  echo "ℹ️  Existing adapter deployment found in ${NS} (prometheus-adapter). Will upgrade it."
fi

# -------- Build seriesQuery & rule snippet --------
if [[ "${REQUIRE_NAMESPACE_LABEL}" == "true" ]]; then
  SERIES_QUERY='kafka_consumergroup_lag{namespace!="",consumergroup!=""}'
  RES_OVERRIDES_YAML='
        overrides:
          namespace:
            resource: namespace'
else
  SERIES_QUERY='kafka_consumergroup_lag{consumergroup!=""}'
  RES_OVERRIDES_YAML='' # no namespace mapping if label not present
fi
echo "ℹ️  seriesQuery = ${SERIES_QUERY}"

# -------- Install / upgrade adapter --------
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
helm upgrade --install prometheus-adapter prometheus-community/prometheus-adapter \
  -n "$NS" \
  --set "prometheus.url=${PROM_URL}" \
  --set "prometheus.port=${PROM_PORT}" \
  --set "logLevel=2" \
  --set "metricsRelistInterval=${METRICS_RELIST_INTERVAL}" \
  --set "resources.requests.cpu=${ADAPTER_REQ_CPU}" \
  --set "resources.requests.memory=${ADAPTER_REQ_MEM}" \
  --set "resources.limits.cpu=${ADAPTER_LIM_CPU}" \
  --set "resources.limits.memory=${ADAPTER_LIM_MEM}" \
  -f - <<EOF
rules:
  default: false
  external:
    - seriesQuery: 'kafka_consumergroup_lag{consumergroup!=""}'   # keep namespace mapping if you require it
      resources:
        overrides:
          namespace:
            resource: namespace
      name:
        as: kafka_consumer_group_lag_worst2
      metricsQuery: |
        # Smooth, floor at 0, then pick the WORST (max) of the two target groups per namespace
        topk(1,
          sum by (namespace, consumergroup) (
            clamp_min(
              avg_over_time(
                kafka_consumergroup_lag{
                  <<.LabelMatchers>>,
                  consumergroup=~"payment-order-psp-call-executor-consumer-group|payment-order-psp-result-updated-consumer-group"
                }[1m]
              ),
              0
            )
          )
        )
EOF

# -------- Wait for deployment & APIService --------
echo "⏳ Waiting for prometheus-adapter rollout..."
kubectl -n "$NS" rollout status deploy/prometheus-adapter

echo "⏳ Waiting for v1beta1.external.metrics.k8s.io APIService to become Available..."
for i in {1..60}; do
  cond="$(kubectl get apiservice v1beta1.external.metrics.k8s.io -o jsonpath='{.status.conditions[?(@.type=="Available")].status}' 2>/dev/null || true)"
  [[ "$cond" == "True" ]] && { echo "✅ APIService is Available."; break; }
  sleep 2
done

# -------- Probe the metric until it shows up --------
ENC_GROUP=$(
  python3 -c 'import urllib.parse, os; print(urllib.parse.quote(os.environ.get("GROUP",""), safe=""))' 2>/dev/null \
  || echo "$GROUP"
)
PATH_Q="/apis/external.metrics.k8s.io/v1beta1/namespaces/${APP_NS}/${EXTERNAL_METRIC_NAME}?labelSelector=consumergroup%3D${ENC_GROUP}"

echo "🔎 Probing external metric for consumergroup='${GROUP}': ${PATH_Q}"
for i in {1..20}; do
  if kubectl get --raw "${PATH_Q}" >/dev/null 2>&1; then
    echo "✅ External metric '${EXTERNAL_METRIC_NAME}' is queryable."
    break
  fi
  [[ $i -eq 20 ]] && echo "⚠️ Metric not queryable yet. Check adapter logs and Prometheus series." >&2
  sleep 2
done

echo "✅ Done."