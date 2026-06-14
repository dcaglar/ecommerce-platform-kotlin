#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
VALUES_TPL="$REPO_ROOT/infra/helm-values/payment-edge-cell-values-azure.yaml"
INGRESS_VALUES="$REPO_ROOT/infra/helm-values/ingress-values.yaml"

# 1) Install/upgrade ingress-nginx
#    AKS + Azure Load Balancer: service.type=LoadBalancer automatically provisions a real Azure public IP.
#    No tunnel needed, no NodePort fallback, no minikube commands.
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true
echo "🚀 Installing/Upgrading ingress-nginx..."
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace -f "$INGRESS_VALUES"
kubectl -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s

# 2) Wait for AKS Azure Load Balancer to assign a public EXTERNAL-IP (typically 60-120s after install)
echo "⏳ Waiting for Azure Load Balancer EXTERNAL-IP..."
EXT_IP=""
for i in {1..30}; do
  EXT_IP="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
    -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"
  [ -z "$EXT_IP" ] && EXT_IP="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
  if [ -n "$EXT_IP" ]; then
    echo "✅ Azure Load Balancer EXTERNAL-IP: $EXT_IP"
    break
  fi
  echo "  (attempt $i/30) No EXTERNAL-IP yet, retrying in 10s..."
  sleep 10
done

if [ -z "$EXT_IP" ]; then
  echo "❌ Azure Load Balancer did not assign an EXTERNAL-IP after 5 minutes. Aborting." >&2
  exit 1
fi

# 3) Compute host + base URL for AKS
#    Use nip.io to create a valid wildcard hostname from the Azure public IP.
INGRESS_HOST="payment.${EXT_IP}.nip.io"
BASE_URL="http://$INGRESS_HOST"

echo "✅ Using Base URL: $BASE_URL (Host: $INGRESS_HOST)"

# 4) Write endpoints.json for downstream scripts / k6 load tests
ENDPOINTS_FILE="$REPO_ROOT/infra/endpoints.json"
echo "{\"payment\": \"$BASE_URL\"}" > "$ENDPOINTS_FILE"
echo "📝 Wrote endpoints to: $ENDPOINTS_FILE"

# 5) Substitute INGRESS_HOST and deploy the edge cell chart
TMP_VALUES="$(mktemp)"; trap 'rm -f "$TMP_VALUES"' EXIT
export INGRESS_HOST
envsubst < "$VALUES_TPL" > "$TMP_VALUES"

echo "⏳ Installing/Upgrading the payment-edge-cell Helm chart..."
helm upgrade --install payment-edge-cell "$REPO_ROOT/charts/payment-edge-cell" \
    -n payment --create-namespace -f "$TMP_VALUES"

echo "✅ payment-edge-cell deployment applied."
echo "Use 'kubectl get pods -n payment' to check status."
kubectl -n payment rollout status statefulset payment-edge-cell --timeout=300s || true

echo ""
echo "✅ Deployed to Azure AKS."
echo "   Host:     $INGRESS_HOST"
echo "   Base URL: $BASE_URL"