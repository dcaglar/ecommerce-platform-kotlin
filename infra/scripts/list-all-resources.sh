#!/usr/bin/env bash
set -euo pipefail

echo "====================================================="
echo "   Azure Subscription Resource Audit"
echo "====================================================="

# Check az login status
if ! az account show >/dev/null 2>&1; then
  echo "❌ You are not logged into Azure."
  echo "Please run 'az login' first, then re-run this script."
  exit 1
fi

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
SUBSCRIPTION_NAME=$(az account show --query name -o tsv)

echo "🔍 Querying all resources for subscription: $SUBSCRIPTION_NAME ($SUBSCRIPTION_ID)..."
echo ""

# Run the Azure Resource Graph query across the entire subscription
az graph query -q "
  Resources 
  | project name, resourceGroup, type, location, createdTime=properties.timeCreated
" --query data --output table
