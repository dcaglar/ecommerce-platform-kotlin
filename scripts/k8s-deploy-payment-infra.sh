#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/.."

# Ensure all required namespaces exist
kubectl apply -f "$REPO_ROOT/infra/k8s/namespaces/all-namespaces.yaml"

# Deploy payment infra components in the correct order
NAMESPACE_PAYMENT=payment
NAMESPACE_AUTH=auth

# payment-db
kubectl apply -f "$REPO_ROOT/infra/k8s/payment-db/deployment.yaml"
kubectl apply -f "$REPO_ROOT/infra/k8s/payment-db/service.yaml"

# redis
kubectl apply -f "$REPO_ROOT/infra/k8s/redis/deployment.yaml"
kubectl apply -f "$REPO_ROOT/infra/k8s/redis/service.yaml"
#
## zookeeper
#kubectl apply -f "$REPO_ROOT/infra/k8s/zookeeper/deployment.yaml"
#kubectl apply -f "$REPO_ROOT/infra/k8s/zookeeper/service.yaml"
#
## kafka
#kubectl apply -f "$REPO_ROOT/infra/k8s/kafka/statefulset.yaml"
#kubectl apply -f "$REPO_ROOT/infra/k8s/kafka/service.yaml"

# keycloak-db
kubectl apply -f "$REPO_ROOT/infra/k8s/keycloak-db/deployment.yaml"
kubectl apply -f "$REPO_ROOT/infra/k8s/keycloak-db/service.yaml"

# keycloak
kubectl apply -f "$REPO_ROOT/infra/k8s/keycloak/deployment.yaml"
kubectl apply -f "$REPO_ROOT/infra/k8s/keycloak/service.yaml"

# Wait for Keycloak pod to be ready
echo "Waiting for Keycloak pod to be ready..."
kubectl wait --for=condition=ready pod -l app=keycloak -n $NAMESPACE_AUTH --timeout=180s

# Ensure keycloak-provision-script ConfigMap exists before running the provisioner job
if [ ! -f "$REPO_ROOT/keycloak/provision-keycloak.sh" ]; then
  echo "Error: $REPO_ROOT/keycloak/provision-keycloak.sh not found!"
  exit 1
fi
# Ensure output directory exists
mkdir -p "$REPO_ROOT/keycloak/output"
kubectl create configmap keycloak-provision-script --from-file=provision-keycloak.sh="$REPO_ROOT/keycloak/provision-keycloak.sh" -n $NAMESPACE_AUTH --dry-run=client -o yaml | kubectl apply -f -

# Run Keycloak provisioner job
echo "Running Keycloak provisioner job..."
kubectl apply -f "$REPO_ROOT/infra/k8s/keycloak/keycloak-provisioner-job.yaml"

# Wait for the job to complete
echo "Waiting for Keycloak provisioner job to complete..."
kubectl wait --for=condition=complete job/keycloak-provisioner -n $NAMESPACE_AUTH --timeout=120s

echo "Payment infra deployed successfully."
