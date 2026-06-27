#!/bin/bash
# Usage: delete.sh <service-name> <environment>
# Example: ./delete.sh payment-central-relay local
set -euo pipefail

usage() {
  echo "Usage: $0 <service-name> <environment>"
  echo "Example: $0 payment-central-relay local"
  exit 1
}
kubectl config set-context orbstack
SERVICE_NAME=${1:-}
ENV=${2:-}

if [ -z "$SERVICE_NAME" ] || [ -z "$ENV" ]; then
  usage
fi

echo "🗑️ Deleting $SERVICE_NAME from $ENV environment..."

helm uninstall -n payment "$SERVICE_NAME" --ignore-not-found || echo "⚠️ Could not uninstall $SERVICE_NAME. Is the cluster reachable?"
echo "✅ Deletion of $SERVICE_NAME complete."
