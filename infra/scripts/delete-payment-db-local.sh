#!/bin/bash
set -euo pipefail

NAMESPACE="payment"

echo "🚀 Helm unistal payment-db resource"
helm uninstall payment-db -n "$NAMESPACE"


echo "🚀 Deleting payment-db pvc"
kubectl delete pvc -n payment  data-payment-db-postgresql-0

echo "🚀 Deleting create-app-db-credentials-job resource"
kubectl -n payment delete jobs.batch create-app-db-credentials-job || true

kubectl -n payment delete jobs.batch grant-app-db-privileges-job || true




