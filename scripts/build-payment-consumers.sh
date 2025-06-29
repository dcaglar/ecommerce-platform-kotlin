#!/bin/bash
set -e
REGION="europe-west4"
PROJECT_ID="ecommerce-platform-dev"
REPO="dcaglar1987"
# Get the script's parent directory (project root)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

docker buildx build --platform linux/amd64 -t ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/payment-consumers:latest -f payment-consumers/Dockerfile .