#!/bin/bash
set -e

echo "Destroying all Terraform-managed resources..."

terraform destroy -auto-approve -var "project_id=ecommerce-platform-dev"

echo "Terraform destroy completed."
