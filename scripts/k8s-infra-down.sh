#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/.."

# Delete infrastructure manifests using absolute paths for location-agnostic behavior
echo "Deleting Keycloak..."
kubectl delete -f "$REPO_ROOT/infra/k8s/keycloak/" --ignore-not-found
echo "Deleting Keycloak DB..."
kubectl delete -f "$REPO_ROOT/infra/k8s/keycloak-db/" --ignore-not-found
echo "Deleting Payment DB..."
kubectl delete -f "$REPO_ROOT/infra/k8s/payment-db/" --ignore-not-found
echo "Deleting Redis..."
kubectl delete -f "$REPO_ROOT/infra/k8s/redis/" --ignore-not-found
echo "Deleting Namespaces..."
kubectl delete -f "$REPO_ROOT/infra/k8s/namespaces/all-namespaces.yaml" --ignore-not-found
