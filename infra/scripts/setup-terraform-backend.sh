#!/usr/bin/env bash
set -euo pipefail

# Names for the Terraform Backend resources
RESOURCE_GROUP_NAME="rg-terraform-state"
STORAGE_ACCOUNT_NAME="tfstateloadtestdc"
CONTAINER_NAME="tfstate"
LOCATION="westeurope"

echo "====================================================="
echo "   Terraform Backend Storage Provisioner"
echo "====================================================="

# 1. Check az login status
if ! az account show >/dev/null 2>&1; then
  echo "❌ You are not logged into Azure."
  echo "Please run 'az login' first, then re-run this script."
  exit 1
fi

echo "🚀 Creating Resource Group: $RESOURCE_GROUP_NAME..."
az group create --name "$RESOURCE_GROUP_NAME" --location "$LOCATION" -o none

echo "🚀 Creating Storage Account: $STORAGE_ACCOUNT_NAME..."
az storage account create --name "$STORAGE_ACCOUNT_NAME" --resource-group "$RESOURCE_GROUP_NAME" --location "$LOCATION" --sku Standard_LRS --encryption-services blob -o none

# Wait a few seconds for RBAC propagation before creating container
sleep 5

echo "🚀 Creating Blob Container: $CONTAINER_NAME..."
az storage container create --name "$CONTAINER_NAME" --account-name "$STORAGE_ACCOUNT_NAME" --auth-mode login -o none

echo "✅ Terraform backend storage successfully created!"
echo ""
echo "Your providers.tf should be configured as follows:"
echo "  backend \"azurerm\" {"
echo "    resource_group_name  = \"$RESOURCE_GROUP_NAME\""
echo "    storage_account_name = \"$STORAGE_ACCOUNT_NAME\""
echo "    container_name       = \"$CONTAINER_NAME\""
echo "    key                  = \"loadtest.terraform.tfstate\""
echo "  }"
