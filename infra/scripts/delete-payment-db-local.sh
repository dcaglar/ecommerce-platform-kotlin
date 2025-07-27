#!/bin/bash
set -euo pipefail

NAMESPACE="payment"

helm uninstall kafka -n "$NAMESPACE"
helm uninstall redis -n "$NAMESPACE"
helm uninstall payment-db -n "$NAMESPACE"
helm uninstall keycloak -n "$NAMESPACE"

kubectl -n payment delete jobs.batch create-app-db-users
kubectl delete pvc -n payment --all


echo "✅ Uninstalled kafka, redis, and payment-db from namespace: $NAMESPACE"