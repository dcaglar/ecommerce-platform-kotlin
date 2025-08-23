#!/bin/bash
set -euo pipefail

# Deletes the monitoring stack installed by:
#  - deploy-monitoring.sh (kube-prometheus-stack)
#  - consumer-lag-metrics.sh (prometheus-adapter)
#
# Safe defaults; flip flags for a full nuke:
#   DELETE_NS=false       # delete the 'monitoring' namespace
#   DELETE_CRDS=false     # delete Prometheus Operator CRDs
#   DELETE_CROSS_NS_SMS=false  # delete ServiceMonitor/PodMonitor CRs in other namespaces
#   FORCE_DELETE_RELEASED_PVS=false # delete leftover PVs stuck in Released for NS
#
# Example (full wipe):
#   DELETE_NS=true DELETE_CRDS=true DELETE_CROSS_NS_SMS=true FORCE_DELETE_RELEASED_PVS=true ./delete-monitoring.sh

NS=${NS:-monitoring}
DELETE_NS=${DELETE_NS:-false}
DELETE_CRDS=${DELETE_CRDS:-false}
DELETE_CROSS_NS_SMS=${DELETE_CROSS_NS_SMS:-false}
FORCE_DELETE_RELEASED_PVS=${FORCE_DELETE_RELEASED_PVS:-false}

echo "🧹 Deleting Prometheus Adapter (external metrics API)…"
helm -n "$NS" uninstall prometheus-adapter --wait --ignore-not-found || true

echo "🧹 Deleting kube-prometheus-stack…"
helm -n "$NS" uninstall prometheus-stack --wait --ignore-not-found || true

# In case APIService objects were left behind (rare, but harmless to attempt)
echo "🧹 Cleaning APIService objects (if present)…"
kubectl delete apiservice v1beta1.external.metrics.k8s.io --ignore-not-found || true
# (We did not install custom.metrics; leave it alone unless you know you created it.)
# kubectl delete apiservice v1beta1.custom.metrics.k8s.io --ignore-not-found || true

echo "🧹 Deleting Prometheus/Alertmanager PVCs (data volumes)…"
kubectl -n "$NS" delete pvc -l app.kubernetes.io/instance=prometheus-stack --ignore-not-found || true
kubectl -n "$NS" delete pvc -l app.kubernetes.io/name=prometheus --ignore-not-found || true
kubectl -n "$NS" delete pvc -l app.kubernetes.io/name=alertmanager --ignore-not-found || true

# Optional: remove ServiceMonitor/PodMonitor CRs you created in *other* namespaces
if [[ "$DELETE_CROSS_NS_SMS" == "true" ]]; then
  echo "🧹 Deleting cross-namespace ServiceMonitors/PodMonitors labeled release=prometheus-stack…"
  # Adjust namespaces or labels if you used different ones
  for KIND in servicemonitors.monitoring.coreos.com podmonitors.monitoring.coreos.com probes.monitoring.coreos.com; do
    # Delete in all namespaces that have the label
    kubectl get "$KIND" -A -l release=prometheus-stack -o name 2>/dev/null | xargs -r kubectl delete --ignore-not-found || true
  done
fi

# Optional: force-delete PVs stuck in Released/Failed that belonged to $NS
if [[ "$FORCE_DELETE_RELEASED_PVS" == "true" ]]; then
  echo "🧹 Deleting PVs in phase Released/Failed whose claimRef.namespace == ${NS}…"
  # Prefer jq if available; otherwise fall back to awk/grep
  if command -v jq >/dev/null 2>&1; then
    kubectl get pv -o json \
      | jq -r --arg ns "$NS" '.items[] | select(.spec.claimRef.namespace==$ns and (.status.phase=="Released" or .status.phase=="Failed")) | .metadata.name' \
      | xargs -r kubectl delete pv || true
  else
    kubectl get pv --no-headers \
      | awk -v ns="$NS" '$0 ~ ns && ($5=="Released" || $5=="Failed") {print $1}' \
      | xargs -r kubectl delete pv || true
  fi
fi

# Optional: remove CRDs installed by kube-prometheus-stack (WARNING: deletes ALL CRs cluster-wide)
if [[ "$DELETE_CRDS" == "true" ]]; then
  echo "🧨 Deleting Prometheus Operator CRDs (cluster-wide, destructive)…"
  CRDS=(
    alertmanagers.monitoring.coreos.com
    podmonitors.monitoring.coreos.com
    probes.monitoring.coreos.com
    prometheuses.monitoring.coreos.com
    prometheusrules.monitoring.coreos.com
    servicemonitors.monitoring.coreos.com
    thanosrulers.monitoring.coreos.com
    scrapeconfigs.monitoring.coreos.com
    alertmanagerconfigs.monitoring.coreos.com
  )
  for c in "${CRDS[@]}"; do
    kubectl delete crd "$c" --ignore-not-found || true
  done
fi

# Finally, (optionally) delete the namespace
if [[ "$DELETE_NS" == "true" ]]; then
  echo "🗑️  Deleting namespace '${NS}'…"
  kubectl delete ns "$NS" --ignore-not-found || true
fi

echo "✅ Monitoring stack deletion complete."