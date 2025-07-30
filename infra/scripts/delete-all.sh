#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"


echo "🚀 Helm uninstall keycloak..."
helm uninstall -n payment  keycloak  || true

echo "🚀 Helm uninstall kafka..."
helm uninstall -n payment  kafka  || true

echo "🚀 Deleting kafka pvcs"
kubectl delete pvc -n payment -l app.kubernetes.io/name=kafka

echo "🚀 Helm uninstall redis..."
helm uninstall -n payment  redis  || true

echo "🚀 Deleting redis pvc"
kubectl delete pvc -n payment -l app.kubernetes.io/name=redis


echo "🚀 Helm uninstall payment-db..."
helm uninstall -n payment  payment-db  || true


echo "🚀 Deleting payment-db pvc"
kubectl delete pvc -n payment  data-payment-db-postgresql-0

echo "🚀 Deleting create-app-db-credentials-job resource"
kubectl -n payment delete jobs.batch create-app-db-credentials-job || true


echo "🚀deleting   payment-platform-config"
helm uninstall -n payment  payment-platform-config  || true


echo "🚀deleting   payment service"
helm uninstall -n payment  payment-service  || true



#helm uninstall -n payment  payment-consumers  || true

