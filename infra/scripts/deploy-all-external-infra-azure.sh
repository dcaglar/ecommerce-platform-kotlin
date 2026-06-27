#!/usr/bin/env bash
# Deploys all external infrastructure (Keycloak, Redis, Kafka, KEDA) to Azure AKS.
# Mirrors deploy-all-external-infra-local.sh exactly — azure environment argument only.
#
# Usage: ./deploy-all-external-infra-azure.sh
set -euo pipefail

trap 'echo "❌ Azure external infra deployment failed on line $LINENO. Command: $BASH_COMMAND"' ERR
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

echo "🛡️  Checking Kubernetes context..."
CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "none")

# If the context is NOT the one we expect(aks-payment-loadtest, then abort!)
if [[ "$CURRENT_CONTEXT" != "aks-payment-loadtest" ]]; then
  echo "❌ Current context is '$CURRENT_CONTEXT'. Refusing to deploy to the wrong cluster!"
  echo "💡 Run: az aks get-credentials --resource-group rg-payment-platform-loadtest --name aks-payment-loadtest"
  exit 1
fi

echo "ℹ️  Deploying to verified context: $CURRENT_CONTEXT"
echo "🚀 Deploying all external infrastructure (Keycloak, Redis, Kafka, KEDA) to Azure..."

# 1. Keycloak
echo "Sending a deployment request of KEYCLOAK to Azure helm..."
"$SCRIPT_DIR/deploy-external-infra.sh" keycloak azure
echo "Deployment request of KEYCLOAK submitted to Azure helm."

# 2. Redis
echo "Sending a deployment request of REDIS to Azure helm..."
"$SCRIPT_DIR/deploy-external-infra.sh" redis azure
echo "Deployment request of REDIS submitted to Azure helm."

# 3. Kafka
echo "Sending a deployment request of KAFKA to Azure helm..."
"$SCRIPT_DIR/deploy-external-infra.sh" kafka azure
echo "Deployment request of KAFKA submitted to Azure helm."

# 4. KEDA — required on Azure for payment-consumers autoscaling
echo "Sending a deployment request of KEDA to Azure helm..."
"$SCRIPT_DIR/deploy-external-infra.sh" keda azure
echo "Deployment request of KEDA submitted to Azure helm."

echo ""
echo "✅ All external infrastructure manifests successfully submitted to Azure Kubernetes via helm."
echo "Kubernetes is now resolving dependencies natively via initContainers."
echo "Check progress via: kubectl get pods -n payment -w"
