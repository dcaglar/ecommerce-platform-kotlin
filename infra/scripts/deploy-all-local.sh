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
helm repo add bitnami https://charts.bitnami.com/bitnami >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true
helm upgrade --install keycloak bitnami/keycloak \
  -n "$NS" --create-namespace \
  --version 17.3.2 \
  -f "$REPO_ROOT/infra/helm-values/keycloak-values-local.yaml"

# 2. Central Database
helm upgrade --install central-db bitnami/postgresql \
  -n "$NS" --create-namespace \
  --version 15.5.1 \
  -f "$REPO_ROOT/infra/helm-values/central-db-values-local.yaml" \
  --set image.tag="16.4.0-debian-12-r0"

# 3. Redis
helm upgrade --install redis bitnami/redis \
  -n "$NS" --create-namespace \
  --version 18.14.0 \
  -f "$REPO_ROOT/infra/helm-values/redis-values-local.yaml"

# 4. Kafka
helm upgrade --install kafka bitnami/kafka \
  -n "$NS" --create-namespace \
  --version 26.6.1 \
  -f "$REPO_ROOT/infra/helm-values/kafka-values-local.yaml"

# 5. KEDA
helm repo add kedacore https://kedacore.github.io/charts >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true
helm upgrade --install keda kedacore/keda \
  -n keda --create-namespace \
  --version 2.12.0 \
  -f "$REPO_ROOT/infra/helm-values/keda-values-local.yaml"



# 6. Ingress-nginx
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/ingress-nginx-values-local.yaml"

# 7. Payment Edge Cell
helm upgrade --install payment-edge-cell "$REPO_ROOT/charts/payment-edge-cell" \
  -n "$NS" --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/payment-edge-cell-values-local.yaml"

# 8. Payment Edge Workers
helm upgrade --install payment-edge-workers "$REPO_ROOT/charts/payment-edge-workers" \
  -n "$NS" --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/payment-edge-workers-values-local.yaml"

# 9. Payment Consumers
helm upgrade --install payment-consumers "$REPO_ROOT/charts/payment-consumers" \
  -n "$NS" --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/payment-consumers-values-local.yaml"

# 10. Payment Central Relay
helm upgrade --install payment-central-relay "$REPO_ROOT/charts/payment-central-relay" \
  -n "$NS" --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/payment-central-relay-values-local.yaml"

echo ""
echo "✅ All manifests successfully submitted to Kubernetes!"
echo "Kubernetes is now resolving dependencies natively via initContainers."
echo "Check progress via: kubectl get pods -n payment -w"
