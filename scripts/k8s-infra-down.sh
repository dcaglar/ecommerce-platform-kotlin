#!/bin/bash
set -e

# Delete infrastructure manifests
echo "Deleting Keycloak..."
kubectl delete -f ../infra/k8s/keycloak/ --ignore-not-found
echo "Deleting Keycloak DB..."
kubectl delete -f ../infra/k8s/keycloak-db/ --ignore-not-found
echo "Deleting Payment DB..."
kubectl delete -f ../infra/k8s/payment-db/ --ignore-not-found
echo "Deleting Redis..."
kubectl delete -f ../infra/k8s/redis/ --ignore-not-found
echo "Deleting Kafka..."
kubectl delete -f ../infra/k8s/kafka/ --ignore-not-found
echo "Deleting Zookeeper..."
kubectl delete -f ../infra/k8s/zookeeper/ --ignore-not-found
echo "Deleting Namespaces..."
kubectl delete -f ../infra/k8s/namespaces/all-namespaces.yaml --ignore-not-found

