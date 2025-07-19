#!/bin/bash
# Usage: ./port-forward-pod-resilient.sh <service> <local_port> <remote_port> <namespace>

SERVICE="$1"
LOCAL_PORT="$2"
REMOTE_PORT="$3"
NAMESPACE="${4:-payment}"

wait_for_pod_ready() {
  local service="$1"
  local namespace="$2"
  local timeout="${3:-120}"
  echo "[WAIT] Waiting for running pod for service '$service' in ns '$namespace' (timeout $timeout s)..."
  for ((i=0; i<timeout; i++)); do
    pod=$(kubectl get pod -n "$namespace" -l app="$service" --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [[ -n "$pod" ]]; then
      echo "[OK] Pod '$pod' is running."
      return 0
    fi
    sleep 1
  done
  echo "[FAIL] Timed out waiting for pod with label 'app=$service'!"
  exit 1
}

wait_for_pod_ready "$SERVICE" "$NAMESPACE"

while true; do
  POD=$(kubectl get pod -n "$NAMESPACE" -l app="$SERVICE" --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
  if [[ -n "$POD" ]]; then
    echo "[INFO] Port-forwarding to POD: $POD"
    kubectl port-forward -n "$NAMESPACE" "$POD" "$LOCAL_PORT:$REMOTE_PORT"
    echo "[WARN] Port-forward for $POD died, retrying in 2s..."
    sleep 2
  else
    echo "[WAIT] Waiting for running pod for $SERVICE..."
    sleep 2
  fi
done