#!/bin/bash
set -e

# Delete payment-service app manifests
echo "Deleting payment-service deployment..."
kubectl delete -f ../infra/k8s/payment/payment-service-deployment.yaml --ignore-not-found
echo "Deleting payment-service service..."
kubectl delete -f ../infra/k8s/payment/payment-service-service.generated.yaml --ignore-not-found
echo "Deleting payment-service configmap..."
kubectl delete -f ../infra/k8s/payment/payment-service-configmap.yaml --ignore-not-found
# Add other app deployments here (e.g., payment-consumers) as needed
