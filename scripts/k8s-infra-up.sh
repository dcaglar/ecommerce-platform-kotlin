#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/.."

# Apply infrastructure manifests using absolute paths for location-agnostic behavior
kubectl apply -f "$REPO_ROOT/infra/k8s/namespaces/all-namespaces.yaml"
kubectl apply -f "$REPO_ROOT/infra/k8s/redis/"
kubectl apply -f "$REPO_ROOT/infra/k8s/payment-db/"
kubectl apply -f "$REPO_ROOT/infra/k8s/keycloak-db/"
kubectl apply -f "$REPO_ROOT/infra/k8s/keycloak/"
