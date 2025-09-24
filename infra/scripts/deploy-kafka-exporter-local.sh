#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

NAMESPACE="payment"
RELEASE_NAME="kafka-exporter"
VALUES_FILE="$REPO_ROOT/infra/helm-values/kafka-exporter-values-local.yaml"

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo update >/dev/null

helm upgrade --install "$RELEASE_NAME" prometheus-community/prometheus-kafka-exporter \
  -n "$NAMESPACE" --create-namespace -f "$VALUES_FILE" \
  --wait --timeout 5m

kubectl -n "$NAMESPACE" rollout status deploy/"$RELEASE_NAME"-prometheus-kafka-exporter --timeout=3m
echo "✅ kafka-exporter is up."