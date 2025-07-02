#!/bin/bash
# Unified build and push script for payment-service Docker image to Google Artifact Registry

set -e
  # Terraform mode: use correct relative path for this repo structure
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  TF_OUTPUT_DIR="$SCRIPT_DIR/../terraform"
  PAYMENT_SERVICE_DIR="$SCRIPT_DIR/../../../payment-service"
  # Ensure build context includes all required directories
  BUILD_CONTEXT="$PAYMENT_SERVICE_DIR"
  PROJECT_ID=$(terraform -chdir=$TF_OUTPUT_DIR output -raw project_id)
  REGION=$(terraform -chdir=$TF_OUTPUT_DIR output -raw region)
  ZONE=$(terraform -chdir=$TF_OUTPUT_DIR output -raw zone)
  REPO=$(terraform -chdir=$TF_OUTPUT_DIR output -raw repository_id)
  IMAGE_NAME="payment-service"
  TAG="latest"  # Or use $(git rev-parse --short HEAD) for commit-based tags


IMAGE_URI="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/${IMAGE_NAME}:${TAG}"

# Authenticate with Google Cloud Artifact Registry
gcloud auth configure-docker

echo "Building Docker image: $IMAGE_URI ..."
docker build -t $IMAGE_URI -f "$PAYMENT_SERVICE_DIR/Dockerfile" "$BUILD_CONTEXT"

echo "Pushing Docker image to Artifact Registry: $IMAGE_URI ..."
docker push $IMAGE_URI

echo "Image pushed: $IMAGE_URI"
# Optionally, write the image URI to a file for the deploy script to read
DEPLOY_IMAGE_FILE="$SCRIPT_DIR/.last-pushed-image.txt"
echo $IMAGE_URI > $DEPLOY_IMAGE_FILE
echo "Image URI written to $DEPLOY_IMAGE_FILE"
