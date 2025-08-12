#!/bin/bash

# Prevent double-start
LOCKFILE="/tmp/k8s-port-forward.lock"
if [ -f "$LOCKFILE" ]; then
    echo "Port-forward script is already running! (Remove $LOCKFILE to force re-run.)"
    exit 1
fi
touch "$LOCKFILE"

# Set namespace vars
PAYMENT_NS=payment
MONITORING_NS=monitoring
LOGGING_NS=logging

# Store all PIDs in an array
PIDS=()

function port_forward {
  svc=$1; local_port=$2; remote_port=$3; ns=$4
  while true; do
    kubectl port-forward svc/$svc $local_port:$remote_port -n $ns
    echo "Port-forward for $svc dropped. Reconnecting in 2s..."
    sleep 2
  done
}

# Trap cleanup: kill all background jobs & remove lockfile on exit
cleanup() {
  echo "Killing all port-forwards..."
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null
  done
  rm -f "$LOCKFILE"
  exit 0
}
trap cleanup SIGINT SIGTERM

# Start port-forwards
port_forward keycloak 8080 8080 $PAYMENT_NS & PIDS+=($!)
port_forward payment-db-postgresql 5432 5432 $PAYMENT_NS & PIDS+=($!)
port_forward payment-service 8081 8080 $PAYMENT_NS & PIDS+=($!)
port_forward payment-consumers 8082 8080 $PAYMENT_NS & PIDS+=($!)
port_forward prometheus-stack-kube-prom-prometheus 9090 9090 $MONITORING_NS & PIDS+=($!)
port_forward prometheus-stack-grafana 3000 80 $MONITORING_NS & PIDS+=($!)
port_forward kafka 9092 9092 $PAYMENT_NS & PIDS+=($!)
#port_forward kibana-kibana 5601 5601 $LOGGING_NS & PIDS+=($!)

echo "All port-forwards running. Press Ctrl+C in this terminal to stop ALL."

wait