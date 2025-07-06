#!/bin/bash
# Usage: k8s-stop-app.sh <deployment> <namespace>

DEPLOYMENT=$1
NS=$2

if [ -z "$DEPLOYMENT" ] || [ -z "$NS" ]; then
  echo "Usage: $0 <deployment> <namespace>"
  exit 1
fi

echo "🛑 Scaling deployment $DEPLOYMENT in namespace $NS to 0 replicas (stopping)..."
kubectl scale deployment "$DEPLOYMENT" -n "$NS" --replicas=0

