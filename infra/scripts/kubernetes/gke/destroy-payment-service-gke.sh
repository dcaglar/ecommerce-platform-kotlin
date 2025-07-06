#!/bin/bash
# Script to destroy all GKE payment-service resources via Terraform

set -e

cd "$(dirname "$0")/../terraform"

echo "[1/2] Checking GCP authentication..."
if ! gcloud auth list --filter=status:ACTIVE --format='value(account)' | grep -q .; then
  echo "No active gcloud account found. Please log in."
  gcloud auth login
else
  echo "Already authenticated with gcloud."
fi

echo "[2/2] Destroying all Terraform-managed resources..."
terraform destroy -auto-approve

echo "All resources have beensas destroyed."