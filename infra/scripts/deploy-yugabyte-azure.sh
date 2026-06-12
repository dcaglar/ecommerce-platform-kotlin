#!/usr/bin/env bash
set -euo pipefail

NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/yugabyte-values-azure.yaml"

# Add YugabyteDB Helm repo
helm repo add yugabytedb https://charts.yugabyte.com >/dev/null 2>&1 || true
helm repo update

# Install/upgrade the chart
helm upgrade --install yugabyte yugabytedb/yugabyte \
  --version 2.23.0 \
  -n "$NS" --create-namespace \
  -f "$VALUES_FILE"

# Wait for TServers to be up
kubectl -n "$NS" rollout status statefulset/yb-tserver --timeout=5m
kubectl -n "$NS" get pods,svc -l app.kubernetes.io/name=yugabyte -o wide
