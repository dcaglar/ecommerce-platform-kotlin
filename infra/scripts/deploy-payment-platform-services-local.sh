#!/usr/bin/env bash
# Deploys all payment platform services to Local Kubvernetes ORb
# local mirror of: deploy-payment-platform-services-local.sh
#
# Usage: ./deploy-payment-platform-services-local.sh
set -euo pipefail

trap 'echo "❌ Local payment platform services deployment failed on line $LINENO. Command: $BASH_COMMAND"' ERR
NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

echo "🛡️  Checking Kubernetes context..."
CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "none")
if [[ "$CURRENT_CONTEXT" == "azure" ]]; then
  echo "❌ Current context is 'azure' — refusing to deploy local workloads to azure cluster."
  echo "💡 Run: az aks get-credentials --resource-group rg-payment-platform-loadtest --name orbstack"
  exit 1
fi
echo "ℹ️  Deploying to context: $CURRENT_CONTEXT"

echo "🚀 Deploying all payment platform services to Local..."

# 1. Payment Edge Cell (payment-service and local edge-db initialized with necessary
#    username and password, and liquibase performs initial table creation)
echo "Sending a deployment request of payment-edge-cell chart (payment-service and local edge-db) to Local helm..."
"$SCRIPT_DIR/deploy.sh" payment-edge-cell local
echo "Deployment request of payment-edge-cell chart was submitted to Local helm."

# 2. Payment Edge Workers (Asynchronous OutboxForwarder job moves local outbox to
#    central outbox, and also Outbox Maintenance job)
echo "Sending a deployment request of payment-edge-workers chart to Local helm..."
"$SCRIPT_DIR/deploy.sh" payment-edge-workers local
echo "Deployment request of payment-edge-workers chart (LocalOutboxStoreAndForwardJob and LocalOutboxMaintenanceJob) was submitted to local helm."

# 3. Central DB — initialized with custom users/roles, liquibase creates schema
echo "Sending a deployment request of central-db chart to LOCAL helm..."
"$SCRIPT_DIR/deploy.sh" central-db local
echo "Deployment request of central-db chart was submitted to LOCAL helm."

# 4. Payment Consumers
echo "Sending a deployment request of payment-consumers chart to local helm..."
"$SCRIPT_DIR/deploy.sh" payment-consumers local
echo "Deployment request of payment-consumers chart was submitted to local helm."

# 5. Payment Central Relay (OutboxRelayJob and CentralOutboxMaintenanceJob)
echo "Sending a deployment request of payment-central-relay chart to local helm..."
"$SCRIPT_DIR/deploy.sh" payment-central-relay local
echo "Deployment request of payment-central-relay chart (OutboxRelayJob and CentralOutboxMaintenanceJob) was submitted to local helm."

echo ""
echo "✅ All manifests successfully submitted to Local Kubernetes via helm."
echo "Kubernetes is now resolving dependencies natively via initContainers."
echo "Check progress via: kubectl get pods -n payment -w"

