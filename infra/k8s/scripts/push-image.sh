#!/bin/bash
# Usage: ./push-image.sh <image-name> [tag]
# Example: ./push-image.sh payment-service latest
set -e

REGION="europe-west4"
ZONE="europe-west4-a"

PROJECT_ID="ecommerce-platform-dev"
REPO="dcaglar1987"

IMAGE_NAME="$1"
TAG="${2:-latest}"

if [ -z "$IMAGE_NAME" ]; then
  echo "Usage: $0 <image-name> [tag]"
  exit 1
fi

docker push ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/${IMAGE_NAME}:${TAG}

