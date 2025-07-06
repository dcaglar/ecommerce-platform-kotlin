#!/bin/bash
set -e

ENV=${1:-local}

SCRIPT_DIR="$(cd "$(dirname "$0")"; pwd)"
TARGET_DIR="$SCRIPT_DIR/../../../k8s/overlays/$ENV/secrets"
mkdir -p "$TARGET_DIR"

# Customize this per environment!
KAFKA_JAAS_CONFIG='org.apache.kafka.common.security.plain.PlainLoginModule required username="U7AOYMOL2L4LV5S7" password="DMMVZ18W4LQMD2146eC2abRH2G8qasD5FXhcliG8hHRf5Y6xTI0xLwXdrMzAGcMu";'
kubectl create secret generic kafka-credentials \
  --from-literal=SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG="$KAFKA_JAAS_CONFIG" \
  -n payment \
  --dry-run=client -o yaml > "$TARGET_DIR/kafka-credentials.yaml"

echo "kafka-credentials.yaml generated in $TARGET_DIR"
