#!/bin/bash
set -e

# Usage: ./k8s-app-up.sh [local|gke]
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

# Apply payment-service app manifests using absolute paths for location-agnostic behavior
kubectl apply -f "$CONFIGMAP_FILE" || true
kubectl apply -f "$SECRET_FILE" || true
kubectl apply -f "$DEPLOYMENT_FILE" || true
kubectl apply -f "$SERVICE_FILE" || true
# Add other app deployments here (e.g., payment-consumers) as needed
