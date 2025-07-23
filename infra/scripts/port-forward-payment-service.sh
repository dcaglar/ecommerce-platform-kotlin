#!/bin/bash
set -e
NAMESPACE=payment

SERVICE=payment-service
LOCAL_PORT=8081
REMOTE_PORT=8080

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Use resilient port-forward for payment-service (auto-reconnect on pod restart)
/bin/bash "$SCRIPT_DIR/kubernetes/port-forward-pod-resilient.sh" $SERVICE $LOCAL_PORT $REMOTE_PORT $NAMESPACE

