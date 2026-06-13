#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
VALUES_TPL="$REPO_ROOT/infra/helm-values/payment-edge-cell-values-azure.yaml"
INGRESS_VALUES="$REPO_ROOT/infra/helm-values/ingress-values.yaml"
# 1) Ensure we don't have two ingress controllers
if minikube addons list  | grep -qE '^- +ingress +enabled'; then
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

# Prefer LoadBalancer EXTERNAL-IP (OrbStack/Minikube Tunnel)
echo "⏳ Detecting LoadBalancer EXTERNAL-IP..."
EXT_IP=""
for _ in {1..10}; do
  EXT_IP="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
    -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"
  [ -z "$EXT_IP" ] && EXT_IP="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
  
  # If we found an IP/Hostname, verify Port 80 is actually open on our host
  if [ -n "$EXT_IP" ]; then
    if nc -zw1 "$EXT_IP" 80 2>/dev/null; then
       echo "✅ Found reachable LoadBalancer: $EXT_IP"
       break
    else
       echo "⚠️  Found $EXT_IP but Port 80 is REFUSED. (Is 'tunnel.sh' running?)"
       EXT_IP=""
    fi
  fi
  sleep 2
done

if [ -n "$EXT_IP" ]; then
  BASE_URL="http://$EXT_IP"
else
  NODE_PORT="$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
    -o jsonpath='{.spec.ports[?(@.port==80)].nodePort}')"
  BASE_URL="http://${MINI_IP}:${NODE_PORT}"
  echo "ℹ️  No reachable LoadBalancer found. Falling back to NodePort: $BASE_URL"
  echo "   Tip: Run 'infra/scripts/tunnel.sh' in a separate terminal to enable Port 80."
fi

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
#   echo "❌ Health check failed. Check ' sudo -E minikube -p newprofile tunnel', Ingress rules, or service endpoints." >&2
#   exit 1
# fi