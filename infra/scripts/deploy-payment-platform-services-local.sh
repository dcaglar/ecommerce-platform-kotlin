#!/usr/bin/env bash
set -euo pipefail

trap 'echo "❌ Local payment platform services deployment failed on line $LINENO. Command: $BASH_COMMAND"' ERR
NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

echo "🛡️  Checking Kubernetes context..."
CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "none")
if [[ "$CURRENT_CONTEXT" != "payment" ]]; then
  echo "⚠️  Current context is '$CURRENT_CONTEXT', but this script requires 'payment'."
  if kubectl config get-contexts payment >/dev/null 2>&1; then
    echo "🔄 Switching context to 'payment'..."
    kubectl config use-context payment
  else
    echo "❌ payment context not found! Is OrbStack running with Kubernetes enabled?"
    exit 1
  fi
fi

echo "🚀 Deployin all payment platform servcices locally"






# 1. Ingress Controller (Nginx) , installed for load balaning support of paymeny-service
echo "Sending a deployment request of  ingress LOAD BALANCER  controller to  local helm "
"$SCRIPT_DIR/deploy-external-infra.sh" ingress-controller local
echo "Deployment request of  ingress  LOAD BALANCER controller was submitted to helm"




# 2. Payment Edge Cell (payment-service and local edge-db initialized with neccesaay username and password,and liquibase perform initial tables creation)
echo "Sending a deployment request of  payment-edge-cell chart (payment-service and local edge-db) to  local helm"
"$SCRIPT_DIR/deploy.sh" payment-edge-cell local
echo "Deployment request of   payment-edge-cell chart (payment-service and local edge-db) was submitted to local  helm"

# 3. Payment Edge Workers(Asyncronous OutboxForwarder job move local outbox to central outbox, and also Outbox Maintenance job)
echo "Sending a deployment request of  payment-edge-workers chart (payment-service and local edge-db) to  local helm"
"$SCRIPT_DIR/deploy.sh" payment-edge-workers local
echo "Deployment request of    payment-edge-workers chart (LocalOutboxStoreAndForwardJob and LocalOutboxMaintenanceJob ) was submitted to local  helm"

# 4. Deploy a tailored, initialized with custom user and roles central-db on local environment
# and liqubiase job create neccesary tables,and start central-db
echo "Sending a deployment request of  central-db chart (initialized with custom user and roles) to  local helm"
"$SCRIPT_DIR/deploy.sh" central-db local
echo "Sending a deployment request of  central-db chart (initialized with custom user and roles,and scheme changes) are applied) to  local helm"


# 5. Payment Consumers. is deployed to local environment
echo "Sending a deployment request of  payment-consumers chart  to  local helm"
"$SCRIPT_DIR/deploy.sh" payment-consumers local
echo "Deployment request of    payment-consumers chart was submitted to local  helm"

# 6. Payment Central Relay is deployed to local environment
echo "Sending a deployment request of  payment-central-relay local chart(includes OutboxRelayJob and CentralOutboxMaintenanceJob )  to  local helm"
"$SCRIPT_DIR/deploy.sh"  payment-central-relay local
echo "Deployment request of    payment-central-relay  chart(includes OutboxRelayJob and CentralOutboxMaintenanceJob)  was submitted to local  helm"


echo ""
echo "✅ All manifests successfully submitted to local Kubernetes via helm"
echo "Kubernetes is now resolving dependencies natively via initContainers."
echo "Check progress via: kubectl get pods -n payment -w"
