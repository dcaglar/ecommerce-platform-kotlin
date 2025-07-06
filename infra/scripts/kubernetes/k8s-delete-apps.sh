#!/bin/bash

set -e

# Usage: k8s-delete-apps.sh <overlay> <namespace>

OVERLAY=$1
NS=$2

if [ -z "$OVERLAY" ] || [ -z "$NS" ]; then
  echo "Usage: $0 <overlay> <namespace>"
  exit 1
fi

# Optional: Delete any overlay secrets yaml files
if [ -d "$OVERLAY/secrets" ]; then
  for s in "$OVERLAY"/secrets/*.yaml; do
    [ -f "$s" ] && echo "🗑️  Deleting secret: $s" && kubectl delete -f "$s" -n "$NS" || true
  done
fi

kubectl delete -k "$OVERLAY" -n "$NS" || true

echo ""
echo "🗑️  Deleted: $OVERLAY from namespace $NS"

