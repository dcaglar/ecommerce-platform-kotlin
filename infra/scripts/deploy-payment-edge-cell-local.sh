#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
VALUES_TPL="$REPO_ROOT/infra/helm-values/payment-edge-cell-values-local.yaml"
INGRESS_VALUES="$REPO_ROOT/infra/helm-values/ingress-values.yaml"
# 1) Install/upgrade ingress-nginx (expects service.type=LoadBalancer in values)
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true
echo "🚀 Installing/Upgrading ingress-nginx..."
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace -f "$INGRESS_VALUES"
kubectl -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s

# 2) Compute host + base URL for OrbStack Native
INGRESS_HOST="payment.k8s.orb.local"
BASE_URL="http://$INGRESS_HOST"

echo "✅ Using Base URL: $BASE_URL (Host: $INGRESS_HOST)"

TMP_VALUES="$(mktemp)"; trap 'rm -f "$TMP_VALUES"' EXIT
export INGRESS_HOST
envsubst < "$VALUES_TPL" > "$TMP_VALUES"


echo "⏳ Installing/Upgrading the payment-edge-cell Helm chart..."
# Notice we deploy from the payment-edge-cell folder but we STILL USE the payment-service docker image!
helm upgrade --install payment-edge-cell  "$REPO_ROOT/charts/payment-edge-cell" \
    -n payment --create-namespace -f "$TMP_VALUES"

echo "✅ payment-edge-cell deployment applied."
echo "Use 'kubectl get pods -n payment' to check status."
kubectl -n payment rollout status statefulset payment-edge-cell  --timeout=180s || true

echo "✅ Deployed."
echo "   Host header: $INGRESS_HOST"
echo "   Base URL:    $BASE_URL"

# 6) Optional health probe (uncomment to fail fast)
# echo "🔎 Probing: $BASE_URL/actuator/health with Host: $INGRESS_HOST"
# if curl -fsS --max-time 5 -H "Host: $INGRESS_HOST" "$BASE_URL/actuator/health" >/dev/null; then
#   echo "✅ Health OK"
# else
#   echo "❌ Health check failed. Check Ingress rules, or service endpoints." >&2
#   exit 1
# fi