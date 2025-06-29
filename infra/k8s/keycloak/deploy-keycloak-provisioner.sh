#!/bin/bash
set -e

NAMESPACE="auth"
CONFIGMAP_NAME="keycloak-provision-script"
SCRIPT_FILE_PATH="keycloak/provision-keycloak.sh"
JOB_FILE_PATH="./infra/k8s/keycloak/keycloak-provisioner-job.yaml"

echo "Checking if namespace $NAMESPACE exists..."
if ! kubectl get ns $NAMESPACE >/dev/null 2>&1; then
  echo "Namespace $NAMESPACE does not exist. Creating..."
  kubectl create namespace $NAMESPACE
fi

echo "Creating/updating ConfigMap $CONFIGMAP_NAME from $SCRIPT_FILE_PATH..."
kubectl create configmap $CONFIGMAP_NAME \
  --from-file=provision-keycloak.sh="$SCRIPT_FILE_PATH" \
  --namespace=$NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

echo "Applying Job manifest $JOB_FILE_PATH..."
kubectl apply -f "$JOB_FILE_PATH" -n $NAMESPACE

echo "Done. Keycloak provisioner deployed."