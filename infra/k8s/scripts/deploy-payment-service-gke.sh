#!/bin/bash
# Master deployment script for payment-service on GKE
# This script automates the full workflow: terraform apply, GCP auth, Docker build/push, and kubectl apply

set -e

# 1. Provision Infrastructure
cd "$(dirname "$0")/../terraform"
echo "[1/6] Initializing and applying Terraform..."
terraform init
terraform apply -auto-approve

# 2. Authenticate with GCP and GKE
PROJECT_ID=$(terraform output -raw project_id)
REGION=$(terraform output -raw region)
CLUSTER_NAME=$(terraform output -raw cluster_name)
cd - > /dev/null

# Check if already authenticated
if ! gcloud auth list --filter=status:ACTIVE --format='value(account)' | grep -q .; then
  echo "No active gcloud account found. Please log in."
  gcloud auth login
else
  echo "Already authenticated with gcloud."
fi
gcloud config set project "$PROJECT_ID"
echo "[2/6] Getting GKE credentials..."
gcloud container clusters get-credentials "$CLUSTER_NAME" --region "$REGION" --project "$PROJECT_ID"

# 3. Build and Push Docker Image
SCRIPT_DIR="$(dirname "$0")"
echo "[3/6] Building and pushing Docker image..."
"$SCRIPT_DIR/build-and-push-payment-service.sh"

# 4. Create required namespaces
echo "[4/10] Creating required namespaces..."
kubectl apply -f ../namespaces/all-namespaces.yaml

# 5. Deploy Zookeeper and wait for readiness
echo "[5/10] Deploying Zookeeper..."
kubectl apply -f ../zookeeper/
echo "Waiting for Zookeeper to be ready..."
kubectl rollout status statefulset/zookeeper -n zookeeper --timeout=180s

# 6. Deploy Kafka and wait for readiness
echo "[6/10] Deploying Kafka..."
kubectl apply -f ../kafka/
echo "Waiting for Kafka to be ready..."
kubectl rollout status statefulset/kafka -n kafka --timeout=180s

# 7. Deploy keycloak-db and wait for readiness
echo "[7/10] Deploying keycloak-db..."
kubectl apply -f ../keycloak-db/
echo "Waiting for keycloak-db to be ready..."
kubectl rollout status statefulset/keycloak-db -n keycloak-db --timeout=180s

# 8. Deploy Keycloak and wait for readiness
echo "[8/10] Deploying Keycloak..."
kubectl apply -f ../keycloak/
echo "Waiting for Keycloak to be ready..."
kubectl rollout status deployment/keycloak -n keycloak --timeout=180s

# 9. Run Keycloak provisioner
echo "[9/10] Running Keycloak provisioner..."
../keycloak/provision-keycloak.sh

# 10. Deploy latest Docker image from Artifact Registry
DEPLOY_IMAGE_FILE="$SCRIPT_DIR/.last-pushed-image.txt"
if [ -f "$DEPLOY_IMAGE_FILE" ]; then
  IMAGE_NAME=$(cat "$DEPLOY_IMAGE_FILE")
  echo "[10/10] Updating payment-service deployment to use image: $IMAGE_NAME ..."
else
  echo "[10/10] WARNING: Image file $DEPLOY_IMAGE_FILE not found. Using default image name."
  IMAGE_NAME="gcr.io/$PROJECT_ID/payment-service:latest"
fi
# Patch the deployment to use the latest image
echo "Patching payment-service deployment with image: $IMAGE_NAME ..."
kubectl set image deployment/payment-service payment-service=$IMAGE_NAME -n payment
# Force a rollout restart to ensure the new image is pulled
echo "Forcing rollout restart for payment-service..."
kubectl rollout restart deployment/payment-service -n payment
# Wait for rollout to complete
echo "Waiting for payment-service rollout to complete..."
kubectl rollout status deployment/payment-service -n payment --timeout=180s

# Deploy payment-service manifests (if needed for config changes)
echo "Applying payment-service manifests..."
kubectl apply -f ../payment/



step10- ./payment-service.sh
step 11-./payment-consumers.sh

step 12- rebuild build payment-service image and push latest to artifca registry
step 13- rebuildbuild payment-consumers image and push latest to artifca registry

step14 - Pulling latest payment-service image grom ArtifactRegistry

step15 - Pulling latest payment-consumer image grom ArtifactRegistry and push PAyment Consumer registery and deploypod""

# Monitor and Debug
echo "Deployment complete. Use the following commands to monitor your deployment:"
step16 - Pulling latest payment-consumer image grom ArtifactRegistry and push PAyment Consumer registery and deploypod""
echo "Deployment  of payment is complete. Use the following commands to monitor your deployment:"
echo "kubectl get pods -n payment"
echo "kubectl logs <pod-name> -n payment"
echo "kubectl port-forward svc/payment-service 8080:8080 -n payment"


