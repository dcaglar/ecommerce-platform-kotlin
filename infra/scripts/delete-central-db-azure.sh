#!/bin/bash
set -euo pipefail

NAMESPACE="payment"

echo "🚀 Helm unistal central-db resource"
helm uninstall central-db -n "$NAMESPACE"  --ignore-not-found


echo "🚀 Deleting central-db pvc"
kubectl delete pvc -n payment  data-central-db-postgresql-0





