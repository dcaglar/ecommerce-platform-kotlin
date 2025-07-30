#!/bin/bash
set -euo pipefail

NAMESPACE="payment"

helm uninstall kafka -n "$NAMESPACE"

kubectl delete pvc -n payment --all

kubectl delete jobs -n payment

echo "✅ Uninstalled kafka, redis, and payment-db from namespace: $NAMESPACE"