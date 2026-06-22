#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"


echo "🚀 Helm uninstall keycloak..."
helm uninstall -n payment  keycloak  --ignore-not-found


echo "🚀 Helm uninstall kafka..."
helm uninstall -n payment  kafka  || true


echo "🚀 Helm uninstall kafka-exporter..."
helm uninstall -n payment  kafka-exporter  || true



echo "🚀 Helm uninstall redis..."
helm uninstall -n payment  redis  --ignore-not-found









echo "🚀 Deleting application services..."
"$SCRIPT_DIR/delete.sh" payment-central-relay local
"$SCRIPT_DIR/delete.sh" payment-consumers local
"$SCRIPT_DIR/delete.sh" payment-edge-workers local
"$SCRIPT_DIR/delete.sh" payment-edge-cell local
"$SCRIPT_DIR/delete.sh" central-db local



echo "🚀 Deleting ALL PVCs in namespace payment (dev wipe)"
kubectl get pvc -n payment -o name | xargs -r kubectl delete -n payment || true

echo "🚀 Deleting PVs orphaned from namespace payment"
PVS=$(kubectl get pv -o jsonpath='{range .items[?(@.spec.claimRef.namespace=="payment")]}{.metadata.name}{"\n"}{end}')
if [ -n "$PVS" ]; then
  for pv in $PVS; do
    echo "🧹 Deleting PV $pv"
    if ! kubectl delete pv "$pv" --wait=true --timeout=30s; then
      echo "🔧 PV $pv stuck; removing finalizers and forcing delete"
      kubectl patch pv "$pv" --type=merge -p '{"metadata":{"finalizers":[]}}' || true
      kubectl delete pv "$pv" --wait=false || true
    fi
  done
  echo "✅ No PVs found for namespace payment"
fi








