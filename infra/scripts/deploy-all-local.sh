#!/usr/bin/env bash
set -euo pipefail

NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

echo "🛡️  Checking Kubernetes context..."
CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "none")
if [[ "$CURRENT_CONTEXT" != "orbstack" ]]; then
  echo "⚠️  Current context is '$CURRENT_CONTEXT', but this script requires 'orbstack'."
  if kubectl config get-contexts orbstack >/dev/null 2>&1; then
    echo "🔄 Switching context to 'orbstack'..."
    kubectl config use-context orbstack
  else
    echo "❌ OrbStack context not found! Is OrbStack running with Kubernetes enabled?"
    exit 1
  fi
fi

echo "🚀 Submitting declarative infrastructure to Kubernetes..."



# 1. Keycloak
deploy-keycloak-local.sh

# 3. Redis
deploy-redis-local.sh

# 4. Kafka
deploy-kafka-local.sh

# 5.
deploy-kafka-local.sh

# 5. KEDA
deploy-keda-local.sh

deploy-ingress-controller-locall.sh

# 6. Ingress-nginx
# helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null 2>&1 || true
# helm repo update >/dev/null 2>&1 || true
# helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
#   -n ingress-nginx --create-namespace \
#   -f "$REPO_ROOT/infra/helm-values/ingress-controllers-values-local.yaml"

# 6.5 Central Database
helm upgrade --install central-db "$REPO_ROOT/charts/central-db" \
  -n "$NS" --create-namespace \
  -f "$REPO_ROOT/charts/central-db/values.yaml" \
  -f "$REPO_ROOT/charts/central-db/local/values.yaml"

# 7. Payment Edge Cell
helm upgrade --install payment-edge-cell "$REPO_ROOT/charts/payment-edge-cell" \
  -n "$NS" --create-namespace \
  -f "$REPO_ROOT/charts/payment-edge-cell/values.yaml" \
  -f "$REPO_ROOT/charts/payment-edge-cell/local/values.yaml"

# 8. Payment Edge Workers
helm upgrade --install payment-edge-workers "$REPO_ROOT/charts/payment-edge-workers" \
  -n "$NS" --create-namespace \
  -f "$REPO_ROOT/charts/payment-edge-workers/values.yaml" \
  -f "$REPO_ROOT/charts/payment-edge-workers/local/values.yaml"

# 9. Payment Consumers
helm upgrade --install payment-consumers "$REPO_ROOT/charts/payment-consumers" \
  -n "$NS" --create-namespace \
  -f "$REPO_ROOT/charts/payment-consumers/values.yaml" \
  -f "$REPO_ROOT/charts/payment-consumers/local/values.yaml"

# 10. Payment Central Relay
helm upgrade --install payment-central-relay "$REPO_ROOT/charts/payment-central-relay" \
  -n "$NS" --create-namespace \
  -f "$REPO_ROOT/charts/payment-central-relay/values.yaml" \
  -f "$REPO_ROOT/charts/payment-central-relay/local/values.yaml"

echo ""
echo "✅ All manifests successfully submitted to Kubernetes!"
echo "Kubernetes is now resolving dependencies natively via initContainers."
echo "Check progress via: kubectl get pods -n payment -w"
