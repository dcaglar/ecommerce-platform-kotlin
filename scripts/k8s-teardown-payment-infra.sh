#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/.."

NAMESPACE_PAYMENT=payment
NAMESPACE_AUTH=auth

# Delete Keycloak provisioner job (if exists)
echo "Deleting Keycloak provisioner job..."
kubectl delete job keycloak-provisioner -n $NAMESPACE_AUTH --ignore-not-found || true

# Delete Keycloak and Keycloak DB deployments and services
echo "Deleting Keycloak and Keycloak DB deployments and services..."
kubectl delete -f "$REPO_ROOT/infra/k8s/keycloak/deployment.yaml" --ignore-not-found || true
kubectl delete -f "$REPO_ROOT/infra/k8s/keycloak/service.yaml" --ignore-not-found || true
kubectl delete -f "$REPO_ROOT/infra/k8s/keycloak-db/deployment.yaml" --ignore-not-found || true
kubectl delete -f "$REPO_ROOT/infra/k8s/keycloak-db/service.yaml" --ignore-not-found || true

# Delete Redis deployment and service
echo "Deleting Redis deployment and service..."
kubectl delete -f "$REPO_ROOT/infra/k8s/redis/deployment.yaml" --ignore-not-found || true
kubectl delete -f "$REPO_ROOT/infra/k8s/redis/service.yaml" --ignore-not-found || true

# Delete Kafka statefulset and service
echo "Deleting Kafka statefulset and service..."
kubectl delete -f "$REPO_ROOT/infra/k8s/kafka/statefulset.yaml" --ignore-not-found || true
kubectl delete -f "$REPO_ROOT/infra/k8s/kafka/service.yaml" --ignore-not-found || true

# Delete Zookeeper deployment and service
echo "Deleting Zookeeper deployment and service..."
kubectl delete -f "$REPO_ROOT/infra/k8s/zookeeper/deployment.yaml" --ignore-not-found || true
kubectl delete -f "$REPO_ROOT/infra/k8s/zookeeper/service.yaml" --ignore-not-found || true

# Delete keycloak-provision-script ConfigMap
echo "Deleting keycloak-provision-script ConfigMap..."
kubectl delete configmap keycloak-provision-script -n $NAMESPACE_AUTH --ignore-not-found || true

echo "Payment infra resources deleted."
