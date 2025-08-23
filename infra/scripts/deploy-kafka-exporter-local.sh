#!/bin/bash
set -euo pipefail
# --- Location Aware ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

NAMESPACE="payment"
RELEASE_NAME="kafka-exporter"
VALUES_FILE="$REPO_ROOT/infra/helm-values/kafka-exporter-values-local.yaml"

# --- Helm Repo Setup ---
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# --- Install / Upgrade ---
helm upgrade --install "$RELEASE_NAME" prometheus-community/prometheus-kafka-exporter \
  -n "$NAMESPACE" --create-namespace -f "$VALUES_FILE"

# Optional: wait for it to be ready
kubectl -n "$NAMESPACE" rollout status deploy/"$RELEASE_NAME"-prometheus-kafka-exporter --timeout=3m