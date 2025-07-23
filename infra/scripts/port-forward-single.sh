#!/bin/bash
# Usage: ./port-forward-single.sh <service> <local_port> <remote_port> <namespace>

wait_for_service_ready() {
  local service="$1"
  local namespace="$2"
  local timeout="${3:-120}" # seconds

  echo "[WAIT] Waiting for service '$service' endpoints in namespace '$namespace' (timeout: $timeout s)..."
  for ((i=0; i<timeout; i++)); do
    ready=$(kubectl get endpoints "$service" -n "$namespace" -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null | wc -w)
    if [[ "$ready" -ge 1 ]]; then
      echo "[OK] Service '$service' has ready endpoints."
      return 0
    fi
    sleep 1
  done
  echo "[FAIL] Timed out waiting for service '$service' to be ready!"
  exit 1
}

SERVICE="$1"
LOCAL_PORT="$2"
REMOTE_PORT="$3"
NAMESPACE="${4:-payment}"

wait_for_service_ready "$SERVICE" "$NAMESPACE"

echo "[port-forward] $SERVICE : localhost:$LOCAL_PORT -> $SERVICE:$REMOTE_PORT (namespace: $NAMESPACE)"
kubectl port-forward svc/$SERVICE $LOCAL_PORT:$REMOTE_PORT -n $NAMESPACE