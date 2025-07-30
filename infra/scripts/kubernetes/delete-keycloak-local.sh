#!/bin/bash
set -euo pipefail

NAMESPACE="payment"

helm uninstall kafka -n "$NAMESPACE"

kubectl -n payment delete jobs.batch --all
kubectl delete pvc -n payment --all

echo "✅ Uninstalled kafka, redis, and payment-db from namespace: $NAMESPACE"