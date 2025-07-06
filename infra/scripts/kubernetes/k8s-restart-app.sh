#!/bin/bash
# Usage: k8s-restart-app.sh <deployment> <namespace>

DEPLOYMENT=$1
NS=$2

if [ -z "$DEPLOYMENT" ] || [ -z "$NS" ]; then
  echo "Usage: $0 <deployment> <namespace>"
  exit 1
fi

echo "🔄 Restarting deployment $DEPLOYMENT in namespace $NS..."
kubectl rollout restart deployment "$DEPLOYMENT" -n "$NS"

