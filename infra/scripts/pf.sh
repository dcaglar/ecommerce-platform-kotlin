#!/bin/bash

NAMESPACE=payment

# Port-forward Keycloak (NodePort 80)
kubectl port-forward svc/keycloak 8080:8080 -n $NAMESPACE &
echo "Keycloak available at http://localhost:8080"

# Port-forward payment-db-postgresql (5432)
kubectl port-forward svc/payment-db-postgresql 5432:5432 -n $NAMESPACE &
echo "Payment DB available at localhost:5432"

# Port-forward payment-service (NodePort 8080)
kubectl port-forward svc/payment-service 8081:8080 -n $NAMESPACE &
echo "Payment Service available at http://localhost:8081"

# Port-forward Prometheus (ClusterIP 9090)
kubectl port-forward svc/prometheus-stack-kube-prom-prometheus 9090:9090 -n $NAMESPACE &
echo "Prometheus available at http://localhost:9090"

# Port-forward Grafana (ClusterIP 80)
kubectl port-forward svc/prometheus-stack-grafana 3000:80 -n $NAMESPACE &
echo "Grafana available at http://localhost:3000"


echo "Port-forwards started. Use 'jobs' to see background processes."