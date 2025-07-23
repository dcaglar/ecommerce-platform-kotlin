#!/bin/bash
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && cd ../.. && pwd)"

# Deploy payment-platform-config
"$REPO_ROOT/infra/scripts/deploy-payment-platform-config.sh"

# Deploy Keycloak
"$REPO_ROOT/infra/scripts/deploy-keycloak-local.sh"

# Deploy payment-db
"$REPO_ROOT/infra/scripts/deploy-payment-db-local.sh"

# Create app DB credentials (job)
"$REPO_ROOT/infra/scripts/create-app-db-credentials.sh"

# Deploy Redis
"$REPO_ROOT/infra/scripts/deploy-redis.sh"

# Deploy Kafka
"$REPO_ROOT/infra/scripts/deploy-kafka-local.sh"

# Deploy payment-service
"$REPO_ROOT/infra/scripts/deploy-payment-service-local.sh"
echo "✅ All services deployed!"
