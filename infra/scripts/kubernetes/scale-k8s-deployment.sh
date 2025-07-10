#!/bin/bash
# Usage: scale-deployment.sh <deployment> <namespace> <replicas>
# Example: scale-deployment.sh payment-consumer payment 0

DEPLOYMENT=$1
NS=$2
REPLICAS=$3

if [ -z "$DEPLOYMENT" ] || [ -z "$NS" ] || [ -z "$REPLICAS" ]; then
  echo "Usage: $0 <deployment> <namespace> <replicas>"
  exit 1
fi

echo "🔄 Scaling deployment $DEPLOYMENT in namespace $NS to $REPLICAS replicas..."
kubectl scale deployment "$DEPLOYMENT" -n "$NS" --replicas="$REPLICAS"