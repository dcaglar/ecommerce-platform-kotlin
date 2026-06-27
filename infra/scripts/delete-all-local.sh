#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
kubectl config use-context orbstack || echo "⚠️ Could not switch to orbstack context. Continuing anyway..."



echo "🚀 Helm uninstall keycloak..."
helm uninstall -n payment  keycloak  --ignore-not-found || echo "⚠️ Could not uninstall keycloak"


echo "🚀 Helm uninstall kafka..."
helm uninstall -n payment  kafka  || true


echo "🚀 Helm uninstall kafka-exporter..."
helm uninstall -n payment  kafka-exporter  || true



echo "🚀 Helm uninstall redis..."
helm uninstall -n payment  redis  --ignore-not-found || echo "⚠️ Could not uninstall redis"









echo "🚀 Deleting application services..."
"$SCRIPT_DIR/delete.sh" payment-central-relay local
"$SCRIPT_DIR/delete.sh" payment-consumers local
"$SCRIPT_DIR/delete.sh" payment-edge-workers local
"$SCRIPT_DIR/delete.sh" payment-edge-cell local
"$SCRIPT_DIR/delete.sh" central-db local



echo "🚀 Deleting ALL PVCs in namespace payment (dev wipe)"
kubectl get pvc -n payment -o name | xargs -r kubectl delete -n payment || true

echo "🚀 Deleting PVs orphaned from namespace payment"
PVS=$(kubectl get pv -o jsonpath='{range .items[?(@.spec.claimRef.namespace=="payment")]}{.metadata.name}{"\n"}{end}' 2>/dev/null || true)
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

echo "🚀 Removing Helm repositories..."
helm repo remove bitnami 2>/dev/null || echo "ℹ️ bitnami repo already removed"
helm repo remove prometheus-community 2>/dev/null || echo "ℹ️ prometheus-community repo already removed"
helm repo remove kedacore 2>/dev/null || echo "ℹ️ kedacore repo already removed"
helm repo remove ingress-nginx 2>/dev/null || echo "ℹ️ ingress-nginx repo already removed"

echo "✅ All local resources deleted."
