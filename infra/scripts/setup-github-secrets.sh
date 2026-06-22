#!/usr/bin/env bash
set -euo pipefail

echo "====================================================="
echo "   Azure & DockerHub GitHub Secrets CLI Provisioner"
echo "====================================================="

# 1. Ensure gh CLI is authenticated
if ! gh auth status >/dev/null 2>&1; then
  echo "❌ You are not logged into the GitHub CLI."
  echo "Please run 'gh auth login' first, then re-run this script."
  exit 1
fi

# 2. Get Docker credentials
if [ -n "${DOCKERHUB_USERNAME:-}" ]; then
  DOCKER_USER="$DOCKERHUB_USERNAME"
  echo "✅ Using DOCKERHUB_USERNAME from environment"
else
  read -p "Enter your Docker Hub Username: " DOCKER_USER
fi

if [ -n "${DOCKER_TOKEN:-}" ]; then
  DOCKER_TOKEN_VAL="$DOCKER_TOKEN"
  echo "✅ Using DOCKER_TOKEN from environment"
else
  echo -n "Enter your Docker Hub Access Token: "
  read -s DOCKER_TOKEN_VAL
  echo ""
fi

# 3. Check az login status
if ! az account show >/dev/null 2>&1; then
  echo "❌ You are not logged into Azure."
  echo "Please run 'az login' first, then re-run this script."
  exit 1
fi

# 4. Create the Azure Service Principal
echo "⏳ Generating Azure Service Principal..."
SUB_ID=$(az account show --query id -o tsv)
SP_JSON=$(az ad sp create-for-rbac --name "github-actions-loadtest" --role contributor --scopes /subscriptions/$SUB_ID -o json)

AZ_CLIENT_ID=$(echo "$SP_JSON" | jq -r .appId)
AZ_CLIENT_SECRET=$(echo "$SP_JSON" | jq -r .password)
AZ_TENANT_ID=$(echo "$SP_JSON" | jq -r .tenant)

echo "⏳ Assigning Storage Blob Data Contributor role for Terraform Remote State..."
az role assignment create --assignee "$AZ_CLIENT_ID" --role "Storage Blob Data Contributor" --scope /subscriptions/$SUB_ID -o none

# 4. Push all secrets directly to GitHub using the gh CLI!
echo "🚀 Pushing secrets directly to your GitHub repository..."

gh secret set DOCKERHUB_USERNAME --body "$DOCKER_USER"
gh secret set DOCKERHUB_TOKEN --body "$DOCKER_TOKEN_VAL"
gh secret set AZURE_SUBSCRIPTION_ID --body "$SUB_ID"
gh secret set AZURE_CLIENT_ID --body "$AZ_CLIENT_ID"
gh secret set AZURE_CLIENT_SECRET --body "$AZ_CLIENT_SECRET"
gh secret set AZURE_TENANT_ID --body "$AZ_TENANT_ID"

echo "✅ All secrets successfully provisioned! You are ready to deploy."
