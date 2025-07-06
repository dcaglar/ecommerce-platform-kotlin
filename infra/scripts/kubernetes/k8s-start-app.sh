#!/bin/bash
# Usage: k8s-start-app.sh <deployment> <namespace> [replicas]

DEPLOYMENT=$1
NS=$2
REPLICAS=${3:-1}

if [ -z "$DEPLOYMENT" ] || [ -z "$NS" ]; then
  echo "Usage: $0 <deployment> <namespace> [replicas]"
  exit 1
fi

echo "🚀 Scaling deployment $DEPLOYMENT in namespace $NS to $REPLICAS replicas..."
kubectl scale deployment "$DEPLOYMENT" -n "$NS" --replicas="$REPLICAS"

