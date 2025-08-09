#!/bin/bash
set -euo pipefail

NAMESPACE="payment"

helm uninstall kafka -n payment || true

kubectl delete pvc -n payment data-kafka-controller-0 || true

kubectl delete pvc -n payment data-kafka-controller-1 || true

kubectl delete pvc -n payment data-kafka-controller-2 || true

kubectl delete pvc -n payment data-kafka-broker-0 || true

kubectl delete pvc -n payment data-kafka-broker-1 || true

kubectl delete pvc -n payment data-kafka-broker-2 || true





echo "✅ Uninstalled  kafka, and deleted pvc from namespace: payment"