#!/bin/bash
set -euo pipefail

NAMESPACE="payment"

helm uninstall kafka -n payment

kubectl delete pvc -n payment data-kafka-controller-0

kubectl delete pvc -n payment data-kafka-controller-1

kubectl delete pvc -n payment data-kafka-controller-2



echo "✅ Uninstalled  kafka, and deleted pvc from namespace: payment"