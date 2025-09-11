#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
VALUES_TPL="$REPO_ROOT/infra/helm-values/payment-service-values-local.yaml"
INGRESS_VALUES="$REPO_ROOT/infra/helm-values/ingress-values.yaml"
ENDPOINTS_JSON="$REPO_ROOT/infra/endpoints.json"

# 1) Ensure we don't have two ingress controllers
if minikube addons list | grep -qE '^- +ingress +enabled'; then
  echo "⚠️  Disabling minikube ingress addon to avoid duplicate controllers..."
  minikube addons disable ingress >/dev/null || true
fi

# 2) Install/upgrade ingress-nginx (expects service.type=LoadBalancer in values)
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true
echo "🚀 Installing/Upgrading ingress-nginx..."
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace -f "$INGRESS_VALUES"
kubectl -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s

# 3) Compute host + base URL
MINI_IP="$(minikube ip)"
INGRESS_HOST="payment.${MINI_IP}.nip.io"

# Prefer LoadBalancer EXTERNAL-IP (requires: `minikube tunnel` running)
echo "⏳ Waiting for LoadBalancer EXTERNAL-IP (run 'minikube tunnel' in another terminal)..."
EXT_IP=""
for _ in {1..60}; do   # 120s total (60 * 2s)
  EXT_IP="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
    -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"
  [ -n "$EXT_IP" ] && break
  sleep 2
done

if [ -n "$EXT_IP" ]; then
  BASE_URL="http://$EXT_IP"
  echo "✅ Using LoadBalancer: EXTERNAL-IP=$EXT_IP  (BASE_URL=$BASE_URL)"
else
  NODE_PORT="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
    -o jsonpath='{.spec.ports[?(@.port==80)].nodePort}')"
  BASE_URL="http://${MINI_IP}:${NODE_PORT}"
  echo "ℹ️  No EXTERNAL-IP yet (LB pending). Falling back to NodePort: $BASE_URL"
  echo "   Tip: start 'minikube tunnel' for a LoadBalancer IP and rerun the script."
fi

# 4) Render chart values (inject INGRESS_HOST) and deploy app
TMP_VALUES="$(mktemp)"; trap 'rm -f "$TMP_VALUES"' EXIT
export INGRESS_HOST
envsubst < "$VALUES_TPL" > "$TMP_VALUES"

echo "🚀 Deploying payment-service..."
helm upgrade --install payment-service "$REPO_ROOT/charts/payment-service" \
  -n payment --create-namespace -f "$TMP_VALUES"
kubectl -n payment rollout status deploy/payment-service --timeout=180s || true

# Ensure Ingress object exists before writing endpoints.json
for _ in {1..30}; do
  kubectl -n payment get ingress payment-service >/dev/null 2>&1 && break
  sleep 2
done

# 5) Write endpoints.json for k6 etc.
mkdir -p "$(dirname "$ENDPOINTS_JSON")"
cat > "$ENDPOINTS_JSON" <<EOF
{ "base_url": "$BASE_URL", "host_header": "$INGRESS_HOST" }
EOF

echo "✅ Deployed."
echo "   Host header: $INGRESS_HOST"
echo "   Base URL:    $BASE_URL"
echo "   endpoints.json → $ENDPOINTS_JSON"

# 6) Optional health probe (uncomment to fail fast)
# echo "🔎 Probing: $BASE_URL/actuator/health with Host: $INGRESS_HOST"
# if curl -fsS --max-time 5 -H "Host: $INGRESS_HOST" "$BASE_URL/actuator/health" >/dev/null; then
#   echo "✅ Health OK"
# else
#   echo "❌ Health check failed. Check 'minikube tunnel', Ingress rules, or service endpoints." >&2
#   exit 1
# fi