#!/bin/bash
set -e
NAMESPACE=payment

# Delete Keycloak deployment, service, and related resources
kubectl delete deployment keycloak -n $NAMESPACE || true
kubectl delete svc keycloak -n $NAMESPACE || true
kubectl delete svc keycloak-headless -n $NAMESPACE || true
kubectl delete deployment keycloak-postgresql -n $NAMESPACE || true
kubectl delete svc keycloak-postgresql -n $NAMESPACE || true
kubectl delete svc keycloak-postgresql-hl -n $NAMESPACE || true

# Optionally delete secrets and configmaps if needed
# kubectl delete secret keycloak-db-credentials -n $NAMESPACE || true
# kubectl delete configmap keycloak-config -n $NAMESPACE || true

echo "✅ Keycloak resources deleted!"

