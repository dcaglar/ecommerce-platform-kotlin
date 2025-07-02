#!/bin/bash
set -e

# Usage: ./k8s-generate-payment-configs.sh [local|gke]
ENVIRONMENT=${1:-local}

# Always use lowercase for config file names and keys for portability
# Use absolute path resolution for location-agnostic behavior
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/.."

if [ "$ENVIRONMENT" = "gke" ]; then
  APP_CONFIG="$REPO_ROOT/payment-service/src/main/resources/application-kubernetesgke.yml"
  CONFIG_KEY="application-kubernetesgke.yml"
  SPRING_DATASOURCE_PASSWORD="payment"
  SPRING_KAFKA_PASSWORD="ZyHGcaLCEdRlYRAZ7zOx3v7GIXIx96/Tr9PStu+itHv8vAZBeUOvM9zJyOTr5Uot"
else
  APP_CONFIG="$REPO_ROOT/payment-service/src/main/resources/application-kuberneteslocal.yml"
  CONFIG_KEY="application-kuberneteslocal.yml"
  SPRING_DATASOURCE_PASSWORD="payment"
  SPRING_KAFKA_PASSWORD="ZyHGcaLCEdRlYRAZ7zOx3v7GIXIx96/Tr9PStu+itHv8vAZBeUOvM9zJyOTr5Uot"
fi

NAMESPACE=payment
CONFIGMAP_PATH="$REPO_ROOT/infra/k8s/payment/configmap/payment-service-configmap.yaml"
SECRET_PATH="$REPO_ROOT/infra/k8s/payment/secret/payment-service-secret.yaml"

if [ ! -f "$APP_CONFIG" ]; then
  echo "Config file $APP_CONFIG does not exist for environment $ENVIRONMENT."
  exit 1
fi

# Generate ConfigMap YAML
echo "Generating ConfigMap YAML for $ENVIRONMENT..."
kubectl create configmap payment-service-config \
  --from-file=$CONFIG_KEY=$APP_CONFIG \
  -n $NAMESPACE \
  --dry-run=client -o yaml > $CONFIGMAP_PATH

echo "ConfigMap YAML generated at $CONFIGMAP_PATH"

# Generate Secret YAML
echo "Generating Secret YAML for $ENVIRONMENT..."
kubectl create secret generic payment-service-secret \
  --from-literal=SPRING_DATASOURCE_PASSWORD=payment \
  --from-literal=SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG="org.apache.kafka.common.security.plain.PlainLoginModule required username='C5EGNWSFH3XKRYTP' password='ZyHGcaLCEdRlYRAZ7zOx3v7GIXIx96/Tr9PStu+itHv8vAZBeUOvM9zJyOTr5Uot';" \
  -n $NAMESPACE \
  --dry-run=client -o yaml > $SECRET_PATH

echo "Secret YAML generated at $SECRET_PATH"
