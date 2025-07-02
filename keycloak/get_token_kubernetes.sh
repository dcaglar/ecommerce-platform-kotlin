#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")"; pwd)"
CLIENT_ID="payment-service"
CLIENT_SECRET="$(grep PAYMENT_SERVICE_CLIENT_SECRET= "${SCRIPT_DIR}/secrets.txt" | cut -d= -f2 | tr -d '\r\n')"
REALM="ecommerce-platform"
NAMESPACE=payment
LOCAL_PORT=18080
REMOTE_PORT=8080

# Start port-forward to Keycloak service in the background, capture logs
PORT_FORWARD_LOG=$(mktemp)
kubectl port-forward svc/keycloak $LOCAL_PORT:$REMOTE_PORT -n $NAMESPACE >"$PORT_FORWARD_LOG" 2>&1 &
PORT_FORWARD_PID=$!

# Ensure port-forward is killed on exit
cleanup() {
  if ps -p $PORT_FORWARD_PID > /dev/null; then
    kill $PORT_FORWARD_PID
  fi
  rm -f "$PORT_FORWARD_LOG"
}
trap cleanup EXIT

# Wait for port-forward to be ready (max 10s)
for i in {1..10}; do
  if nc -z localhost $LOCAL_PORT; then
    break
  fi
  sleep 1
done

if ! nc -z localhost $LOCAL_PORT; then
  echo "❌ Port-forward to Keycloak failed:"
  cat "$PORT_FORWARD_LOG"
  exit 1
fi

KC_URL="http://localhost:$LOCAL_PORT/realms/$REALM/protocol/openid-connect/token"
echo "🔐 Getting access token for client '$CLIENT_ID' and clientsecret '$CLIENT_SECRET' in realm '$REALM'..."

ACCESS_TOKEN=$(curl -s -X POST "$KC_URL" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" | jq -r .access_token)

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
  echo "❌ Failed to obtain access token"
  exit 1
fi

echo "$ACCESS_TOKEN" > "${SCRIPT_DIR}/access.token"
echo "✅ Token saved to ${SCRIPT_DIR}/access.token"
