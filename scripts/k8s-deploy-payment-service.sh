#!/bin/bash
set -e

# Usage: ./k8s-deploy-payment-service.sh [local|gke]
ENVIRONMENT=${1:-local}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/.."

# Ensure output directory exists for local secret output (for Keycloak provisioning)
mkdir -p "$REPO_ROOT/keycloak/output"

echo "[INFO] Environment: $ENVIRONMENT"
if [ "$ENVIRONMENT" = "local" ]; then
  echo "[INFO] Building Docker image for payment-service:latest ..."
  # Always build from the monorepo root so all modules are available
  (cd "$REPO_ROOT" && docker build -f payment-service/Dockerfile -t payment-service:latest .)
  # If using kind, uncomment the next line:
  # kind load docker-image payment-service:latest
  echo "[INFO] Docker image built for local Kubernetes."
else
  echo "[INFO] Skipping local Docker build. Ensure image is pushed to registry for GKE."
fi

# Generate ConfigMap and Secret YAMLs for the selected environment
"$SCRIPT_DIR"/k8s-generate-payment-configs.sh $ENVIRONMENT

# Apply ConfigMap and Secret
kubectl apply -f "$REPO_ROOT/infra/k8s/payment/configmap/payment-service-configmap.yaml"
kubectl apply -f "$REPO_ROOT/infra/k8s/payment/secret/payment-service-secret.yaml"

# Force rollout restart to ensure pods use the latest config/secret
kubectl rollout restart deployment/payment-service -n payment

# Prepare and apply Service manifest with correct type
echo "Preparing Service manifest for $ENVIRONMENT..."
SERVICE_MANIFEST_ORIG="$REPO_ROOT/infra/k8s/payment/payment-service-service.yaml"
SERVICE_MANIFEST_TMP="$REPO_ROOT/infra/k8s/payment/payment-service-service.generated.yaml"
if [ "$ENVIRONMENT" == "gke" ]; then
  SERVICE_TYPE="LoadBalancer"
else
  SERVICE_TYPE="NodePort"
fi
sed "s/{{SERVICE_TYPE}}/$SERVICE_TYPE/g" $SERVICE_MANIFEST_ORIG > $SERVICE_MANIFEST_TMP
kubectl apply -f $SERVICE_MANIFEST_TMP

echo "Service manifest applied with type $SERVICE_TYPE."

# Apply Deployment (use environment-specific deployment file)
if [ "$ENVIRONMENT" = "gke" ]; then
  kubectl apply -f "$REPO_ROOT/infra/k8s/payment/deployment-gke.yaml"
else
  kubectl apply -f "$REPO_ROOT/infra/k8s/payment/deployment-local.yaml"
fi

echo "Payment service deployed successfully for $ENVIRONMENT."
