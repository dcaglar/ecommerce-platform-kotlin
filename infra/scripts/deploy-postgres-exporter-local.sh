#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"


VALUES_FILE="$REPO_ROOT/infra/helm-values/postgresql-exporter-values-local.yaml"
# Configuration
NAMESPACE="payment"
RELEASE_NAME="postgres-exporter"

echo "▶️  Deploying Postgres Exporter..."

# Add/Update Repo
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null
helm repo update >/dev/null

# Deploy
# We use the community chart: prometheus-postgres-exporter/
helm upgrade --install "$RELEASE_NAME" prometheus-community/prometheus-postgres-exporter \
  -n "$NAMESPACE" --create-namespace \
  -f "$VALUES_FILE" \
  --wait --timeout 5m


echo "⏳ Waiting for Exporter Pod..."
kubectl -n "$NAMESPACE" rollout status deploy/"$RELEASE_NAME"-prometheus-postgres-exporter --timeout=3m

echo "✅ Postgres Exporter is up and running."
echo "   Target DB: payment-db-postgresql.payment.svc"
echo "   Metrics URL: http://localhost:9187/metrics (via port-forward)"