#!/bin/bash
# Usage: build-and-push-consumers.sh <dockerhub-username> <tag>
# Example: ./build-and-push-consumers.sh mydockeruser v1.0.0
set -e

# Set fallback values
default_user="dcaglar1987"
default_tag="latest"

DOCKERHUB_USER=${1:-$default_user}
TAG=${2:-$default_tag}

if [ -z "$DOCKERHUB_USER" ] || [ -z "$TAG" ]; then
  echo "Usage: $0 <dockerhub-username> <tag>"
  exit 1
fi

# Prompt for PAT token
read -s -p "Enter your Docker PAT token: " DOCKER_TOKEN
echo

echo "Logging in to Docker Hub..."
echo "$DOCKER_TOKEN" | docker login --username "$DOCKERHUB_USER" --password-stdin

SERVICE_DIR="$(dirname "$0")/../../../payment-consumers"
REPO_ROOT="$(dirname "$0")/../../.."
cd "$SERVICE_DIR"


cd "$REPO_ROOT"
# Build Docker image from root, specifying Dockerfile in payment-consumers
docker build -f payment-consumers/Dockerfile -t "$DOCKERHUB_USER/payment-consumers:$TAG" .

echo "Pushing to Docker Hub..."
docker push "$DOCKERHUB_USER/payment-consumers:$TAG"

echo "✅ Build and push complete: $DOCKERHUB_USER/payment-consumers:$TAG"
