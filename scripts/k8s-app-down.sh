#!/bin/bash
set -e

# Usage: ./k8s-app-down.sh [local|gke]
ENVIRONMENT=${1:-local}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd)"
REPO_ROOT="$SCRIPT_DIR/.."

PAYMENT_PATH="$REPO_ROOT/infra/k8s/payment"

if [ "$ENVIRONMENT" = "gke" ]; then
  CONFIGMAP_FILE="$PAYMENT_PATH/configmap/payment-service-configmap-gke.yaml"
  SECRET_FILE="$PAYMENT_PATH/secret/payment-service-secret-gke.yaml"
  DEPLOYMENT_FILE="$PAYMENT_PATH/deployment-gke.yaml"
  SERVICE_FILE="$PAYMENT_PATH/payment-service-service-gke.yaml"
else
  CONFIGMAP_FILE="$PAYMENT_PATH/configmap/payment-service-configmap.yaml"
  SECRET_FILE="$PAYMENT_PATH/secret/payment-service-secret.yaml"
  DEPLOYMENT_FILE="$PAYMENT_PATH/deployment-local.yaml"
  SERVICE_FILE="$PAYMENT_PATH/payment-service-service.generated.yaml"
fi

# Delete payment-service app manifests using absolute paths for location-agnostic behavior
kubectl delete -f "$SERVICE_FILE" --ignore-not-found || true
kubectl delete -f "$DEPLOYMENT_FILE" --ignore-not-found || true
kubectl delete -f "$SECRET_FILE" --ignore-not-found || true
kubectl delete -f "$CONFIGMAP_FILE" --ignore-not-found || true
# Add other app deletions here (e.g., payment-consumers) as needed
