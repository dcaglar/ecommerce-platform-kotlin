#!/usr/bin/env bash
set -euo pipefail

# --- Config (override via env) -----------------------------------------------
NS=${NS:-monitoring}                         # where adapter & kube-prom live
APP_NS=${APP_NS:-payment}                    # where your HPA / app live
EXTERNAL_METRIC_NAME=${EXTERNAL_METRIC_NAME:-kafka_consumer_group_lag_worst2}
PROM_URL=${PROM_URL:-http://prometheus-stack-kube-prom-prometheus.monitoring.svc.cluster.local}
PROM_PORT=${PROM_PORT:-9090}
METRICS_RELIST_INTERVAL=${METRICS_RELIST_INTERVAL:-30s}

echo "▶️  Installing/Upgrading prometheus-adapter in ns=${NS}"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true

helm upgrade --install prometheus-adapter prometheus-community/prometheus-adapter \
  -n "${NS}" --create-namespace \
  --set prometheus.url="${PROM_URL}" \
  --set prometheus.port="${PROM_PORT}" \
  --set logLevel=2 \
  --set metricsRelistInterval="${METRICS_RELIST_INTERVAL}" \
  -f - <<'EOF'
rules:
  default: false
  external:
    - seriesQuery: 'kafka_consumergroup_lag{namespace!="",consumergroup!=""}'
      resources:
        overrides:
          namespace:
            resource: namespace
      name:
        as: kafka_consumer_group_lag_worst2
      metricsQuery: |
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

echo "⏳ Waiting for prometheus-adapter rollout..."
kubectl -n "${NS}" rollout status deploy/prometheus-adapter --timeout=3m

echo "⏳ Waiting for v1beta1.external.metrics.k8s.io APIService..."
for i in {1..60}; do
  cond="$(kubectl get apiservice v1beta1.external.metrics.k8s.io -o jsonpath='{.status.conditions[?(@.type=="Available")].status}' 2>/dev/null || true)"
  [[ "$cond" == "True" ]] && { echo "✅ APIService Available"; break; }
  sleep 2
done

echo "🔎 Listing external metrics group..."
kubectl get --raw "/apis/external.metrics.k8s.io/v1beta1" | jq .

echo "🔎 Probing ${EXTERNAL_METRIC_NAME} in ns=${APP_NS} ..."
kubectl get --raw "/apis/external.metrics.k8s.io/v1beta1/namespaces/${APP_NS}/${EXTERNAL_METRIC_NAME}" | jq .

cat <<TIP

📌 If the probe 404s:
  - Confirm Prometheus sees your kafka-exporter series:
      kubectl -n ${NS} port-forward svc/prometheus-stack-kube-prom-prometheus 9090:9090
      # then open http://localhost:9090 and query:
      #   kafka_consumergroup_lag{consumergroup="payment-order-psp-call-executor-consumer-group"}
  - Ensure labels in metricsQuery (namespace, consumergroup) match your series.
  - Re-check the adapter’s Prometheus URL/port.

📈 HPA quick checks (assuming HPA named 'payment-consumers' in ns '${APP_NS}'):
  kubectl -n ${APP_NS} describe hpa/payment-consumers
  kubectl -n ${APP_NS} get hpa
  # test the exact external metric path HPA uses:
  kubectl get --raw "/apis/external.metrics.k8s.io/v1beta1/namespaces/${APP_NS}/${EXTERNAL_METRIC_NAME}" | jq .

TIP

echo "✅ Done."