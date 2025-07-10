#!/bin/bash
# port-forward-single.sh
# Usage: ./port-forward-single.sh <service> <local_port> <remote_port> <namespace>

SERVICE=$1
LOCAL_PORT=$2
REMOTE_PORT=$3
NAMESPACE=$4

echo "[port-forward] $SERVICE : localhost:$LOCAL_PORT -> $SERVICE:$REMOTE_PORT (namespace: $NAMESPACE)"
kubectl port-forward svc/$SERVICE $LOCAL_PORT:$REMOTE_PORT -n $NAMESPACE

