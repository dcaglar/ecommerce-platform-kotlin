#!/bin/bash
# Opens a new Terminal tab for each infra service port-forward on macOS, or runs in background on Linux/other

set -e

NAMESPACE="payment"

echo "Starting port-forwards..."

kubectl port-forward -n $NAMESPACE svc/keycloak 8080:8080 &
kubectl port-forward -n $NAMESPACE svc/payment-service 8081:8080 &
kubectl port-forward -n $NAMESPACE svc/payment-db-postgresql 5432:5432 &

wait

echo "All port-forwards started."