#!/bin/bash
# Usage: build-and-push-payment-central-relay-docker-repo.sh <dockerhub-username> <tag>
# Example: ./build-and-push-payment-central-relay-docker-repo.sh mydockeruser v1.0.0
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

SERVICE_DIR="$(dirname "$0")/../../payment-central-relay"
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$SERVICE_DIR"

cd "$REPO_ROOT"
# Build Docker image from root, specifying Dockerfile in payment-central-relay
docker build -f payment-central-relay/Dockerfile -t "$DOCKERHUB_USER/payment-central-relay:$TAG" .

echo "Pushing to Docker Hub..."
docker push "$DOCKERHUB_USER/payment-central-relay:$TAG"

echo "✅ Build and push complete: $DOCKERHUB_USER/payment-central-relay:$TAG"