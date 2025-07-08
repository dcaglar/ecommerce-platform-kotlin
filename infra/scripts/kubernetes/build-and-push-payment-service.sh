#!/bin/bash
# Usage: build-and-push-payment-service.sh <dockerhub-username> <tag>
# Example: ./build-and-push-payment-service.sh mydockeruser v1.0.0

set -e

DOCKERHUB_USER=$1
TAG=$2

if [ -z "$DOCKERHUB_USER" ] || [ -z "$TAG" ]; then
  echo "Usage: $0 <dockerhub-username> <tag>"
  exit 1
fi

SERVICE_DIR="$(dirname "$0")/../../../payment-service"
REPO_ROOT="$(dirname "$0")/../../.."

cd "$SERVICE_DIR"
cd "$REPO_ROOT"

# Build Docker image from root, specifying Dockerfile in payment-service
docker build -f payment-service/Dockerfile -t "$DOCKERHUB_USER/payment-service:$TAG" .

echo "Pushing to Docker Hub..."
docker push "$DOCKERHUB_USER/payment-service:$TAG"

echo "✅ Build and push complete: $DOCKERHUB_USER/payment-service:$TAG"