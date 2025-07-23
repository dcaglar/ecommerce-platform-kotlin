#!/bin/bash
set -e
NAMESPACE=payment

# Payment DB (Postgres)
kubectl port-forward svc/payment-db-postgresql 5432:5432 -n $NAMESPACE &

# Keycloak (NodePort 32080 → local 8080)
kubectl port-forward svc/keycloak 8080:80 -n $NAMESPACE &

# Payment Service (NodePort 32081 → local 8081)
kubectl port-forward svc/payment-service 8081:8080 -n $NAMESPACE &

# Wait and show info
echo "Port forwarding enabled:"
echo "  - Payment DB:      localhost:5432"
echo "  - Keycloak:        localhost:8080"
echo "  - Payment Service: localhost:8081"
echo "Press Ctrl+C to stop all port-forwards."
wait

