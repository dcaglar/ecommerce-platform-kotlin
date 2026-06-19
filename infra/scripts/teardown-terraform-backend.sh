#!/usr/bin/env bash
set -euo pipefail

RESOURCE_GROUP_NAME="rg-terraform-state"

echo "====================================================="
echo "   Terraform Backend Storage Teardown"
echo "====================================================="

echo "⚠️ WARNING: This will completely destroy the Terraform state!"
echo "If your cluster is still running, you will orphan it."
read -p "Are you sure you want to proceed? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    echo "Aborted."
    exit 1
fi

if ! az account show >/dev/null 2>&1; then
  echo "❌ You are not logged into Azure."
  echo "Please run 'az login' first, then re-run this script."
  exit 1
fi

echo "🗑️ Deleting Resource Group: $RESOURCE_GROUP_NAME..."
az group delete --name "$RESOURCE_GROUP_NAME" --yes

echo "✅ Terraform backend storage successfully destroyed!"
