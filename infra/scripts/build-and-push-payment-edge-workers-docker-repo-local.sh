#!/bin/bash
# Usage: build-and-push-payment-service-docker-repo-local.sh <dockerhub-username> <tag>
# Example: ./build-and-push-payment-service-docker-repo-local.sh mydockeruser v1.0.0
set -e
export DOCKER_BUILDKIT=1

# Set fallback values
default_user="dcaglar1987"
default_tag="latest"

DOCKERHUB_USER=${1:-$default_user}
TAG=${2:-$default_tag}

if [ -z "$DOCKERHUB_USER" ] || [ -z "$TAG" ]; then
  echo "Usage: $0 <dockerhub-username> <tag>"
  exit 1
fi

echo "Logging in to Docker Hub..."
echo "$DOCKER_TOKEN" | docker login --username "$DOCKERHUB_USER" --password "$DOCKER_TOKEN"

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

# Build Docker image from root, specifying Dockerfile in payment-edge-workers
docker build -f payment-edge-workers/Dockerfile -t "$DOCKERHUB_USER/payment-edge-workers:$TAG" .

echo "Pushing to Docker Hub..."
docker push "$DOCKERHUB_USER/payment-edge-workers:$TAG"

echo "✅ Build and push complete: $DOCKERHUB_USER/payment-edge-workers:$TAG"