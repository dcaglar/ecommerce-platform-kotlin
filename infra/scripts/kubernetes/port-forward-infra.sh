#!/bin/bash
# Opens a new Terminal tab for each infra service port-forward on macOS, or runs in background on Linux/other

set -e

NAMESPACE="payment"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

function port_forward_mac_resilient_payment_service() {
  SERVICE=payment-service
  LOCAL_PORT=8081
  REMOTE_PORT=8080
  osascript -e 'tell domain "Terminal" to activate' \
            -e "tell application \"Terminal\" to do script \"/bin/bash '$SCRIPT_DIR/port-forward-pod-resilient.sh' $SERVICE $LOCAL_PORT $REMOTE_PORT $NAMESPACE\""
  sleep 1
}

function port_forward_mac() {
  SERVICE=$1
  LOCAL_PORT=$2
  REMOTE_PORT=$3
  osascript -e 'tell domain "Terminal" to activate' \
            -e "tell application \"Terminal\" to do script \"/bin/bash '$SCRIPT_DIR/port-forward-single.sh' $SERVICE $LOCAL_PORT $REMOTE_PORT $NAMESPACE\""
  sleep 1
}

function port_forward_linux() {
  SERVICE=$1
  LOCAL_PORT=$2
  REMOTE_PORT=$3
  nohup bash "$SCRIPT_DIR/port-forward-single.sh" "$SERVICE" "$LOCAL_PORT" "$REMOTE_PORT" "$NAMESPACE" > "$SCRIPT_DIR/port-forward-$SERVICE.log" 2>&1 &
  echo "Started port-forward for $SERVICE in background (log: $SCRIPT_DIR/port-forward-$SERVICE.log)"
}

function port_forward() {
  SERVICE=$1
  LOCAL_PORT=$2
  REMOTE_PORT=$3
  case "$(uname)" in
    Darwin)
      port_forward_mac "$SERVICE" "$LOCAL_PORT" "$REMOTE_PORT"
      ;;
    Linux)
      port_forward_linux "$SERVICE" "$LOCAL_PORT" "$REMOTE_PORT"
      ;;
    *)
      echo "Please run: bash $SCRIPT_DIR/port-forward-single.sh $SERVICE $LOCAL_PORT $REMOTE_PORT $NAMESPACE in a new terminal. (Unsupported OS: $(uname))"
      ;;
  esac
}

echo "Starting port-forwards..."

port_forward keycloak 8080 8080
#port_forward kibana 5601 5601
port_forward grafana-service 3000 3000
port_forward prometheus-service 9090 9090
port_forward payment-db 5432 5432
port_forward pyroscope 4040 4040

# Only this one gets auto-reconnect on pod restart:
port_forward_mac_resilient_payment_service

echo "All port-forwards started."