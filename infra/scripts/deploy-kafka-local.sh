#!/usr/bin/env bash
set -euo pipefail

NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/kafka-values-local.yaml"

# Add/update Bitnami charts repo (this is the Helm *chart* repo, not OCI images)
helm repo add bitnami https://charts.bitnami.com/bitnami >/dev/null 2>&1 || true
helm repo update



# Install/upgrade the chart (specific, stable version)
helm upgrade --install kafka bitnami/kafka \
  --version 32.3.14 \
  -n "$NS" --create-namespace \
  -f "$VALUES_FILE"

# Wait for it to be up and show services
kubectl -n "$NS" rollout status statefulset/kafka-controller --timeout=5m
kubectl -n "$NS" get pods,svc -l app.kubernetes.io/instance=kafka -o wide