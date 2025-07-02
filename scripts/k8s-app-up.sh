#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/.."

# Apply payment-service app manifests
kubectl apply -f "$REPO_ROOT/infra/k8s/payment/payment-service-configmap.yaml"
kubectl apply -f "$REPO_ROOT/infra/k8s/payment/payment-service-deployment.yaml"
# Add other app deployments here (e.g., payment-consumers) as needed
