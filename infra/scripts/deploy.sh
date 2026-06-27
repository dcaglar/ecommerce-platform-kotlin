#!/bin/bash
# Usage: deploy.sh <service-name> <environment>
# Example: ./deploy.sh payment-central-relay local
set -euo pipefail

usage() {
  echo "Usage: $0 <service-name> <environment>"
  echo "Example: $0 payment-central-relay local"
  echo "Example: $0 payment-edge-cell azure"
  exit 1
}

SERVICE_NAME=${1:-}
ENV=${2:-}

if [ -z "$SERVICE_NAME" ] || [ -z "$ENV" ]; then
  usage
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# For some services like central-db, the chart name is the same as the service name
CHART_ROOT="$REPO_ROOT/charts/$SERVICE_NAME"

cd "$REPO_ROOT"

# Determine which secrets to load based on the service and environment
SECRET_ARGS=""
if [[ "$SERVICE_NAME" == "payment-central-relay" ]]; then
  SECRET_ARGS="-f secrets://$REPO_ROOT/central-db-sops-secrets.yaml"
elif [[ "$SERVICE_NAME" == "payment-edge-cell" ]]; then
  SECRET_ARGS="-f secrets://$REPO_ROOT/edge-cell-sops-secrets.yaml"
elif [[ "$SERVICE_NAME" == "payment-edge-workers" ]]; then
  SECRET_ARGS="-f secrets://$REPO_ROOT/edge-cell-sops-secrets.yaml -f secrets://$REPO_ROOT/central-db-sops-secrets.yaml"
elif [[ "$SERVICE_NAME" == "central-db" ]]; then
  SECRET_ARGS="-f secrets://$REPO_ROOT/central-db-sops-secrets.yaml"
elif [[ "$SERVICE_NAME" == "payment-consumers" ]]; then
  SECRET_ARGS="-f secrets://$REPO_ROOT/central-db-sops-secrets.yaml"
fi

# Define the base helm command depending on if we need secrets
if [[ -n "$SECRET_ARGS" ]]; then
  HELM_CMD="helm secrets upgrade"
else
  HELM_CMD="helm upgrade"
fi

echo "🚀 Deploying $SERVICE_NAME to $ENV environment..."

if [[ "$ENV" == "local" ]]; then
  # Some services like keycloak, redis, kafka might not be in the 'charts' directory in the same way,
  # but they are usually handled by their own scripts or helm repos. We will assume for now this handles the custom charts.
  if [ ! -d "$CHART_ROOT" ]; then
    echo "❌ Error: Chart directory $CHART_ROOT does not exist."
    exit 1
  fi
  
  echo "📦 Updating helm dependencies..."
  helm dependency update "$CHART_ROOT"
  
  $HELM_CMD --install "$SERVICE_NAME" "$CHART_ROOT" \
    -n payment --create-namespace \
    -f "$CHART_ROOT/values.yaml" \
    -f "$CHART_ROOT/$ENV/values.yaml" \
    $SECRET_ARGS
    
  # Clean up the downloaded .tgz dependencies so they don't pollute the IDE/codebase
  rm -rf "$CHART_ROOT/charts"
  rm -f "$CHART_ROOT/Chart.lock"
  
  echo "🔄 Forcing pod restart to pull the latest image..."
  kubectl rollout restart deployment "$SERVICE_NAME" -n payment 2>/dev/null || true
  kubectl rollout restart statefulset "$SERVICE_NAME" -n payment 2>/dev/null || true

elif [[ "$ENV" == "azure" ]]; then
  if [ ! -d "$CHART_ROOT" ]; then
    echo "❌ Error: Chart directory $CHART_ROOT does not exist."
    exit 1
  fi

  echo "📦 Updating helm dependencies..."
  helm dependency update "$CHART_ROOT"

  $HELM_CMD --install "$SERVICE_NAME" "$CHART_ROOT" \
    -n payment --create-namespace \
    -f "$CHART_ROOT/azure/values.yaml" \
    $SECRET_ARGS

  # Clean up the downloaded .tgz dependencies so they don't pollute the IDE/codebase
  rm -rf "$CHART_ROOT/charts"
  rm -f "$CHART_ROOT/Chart.lock"
else
  echo "❌ Unknown environment: $ENV"
  exit 1
fi

echo "✅ Deployment of $SERVICE_NAME to $ENV complete."
