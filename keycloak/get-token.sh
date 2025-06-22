#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")"; pwd)"      # Always points to the script's directory
echo "Script name: $0"
NETWORK=auth-net
CLIENT_ID="payment-service"

CLIENT_SECRET="$(grep PAYMENT_SERVICE_CLIENT_SECRET= "${SCRIPT_DIR}/secrets.txt" | cut -d= -f2 | tr -d '\r\n')"
log() { echo "[$(date +'%H:%M:%S')] $*" >&2; }

REALM="ecommerce-platform"
KC_URL="http://keycloak:8080/realms/$REALM/protocol/openid-connect/token"
log "🔐 Getting access token for client '$CLIENT_ID' and clientsecret '$CLIENT_SECRET' in realm '$REALM'..."

ACCESS_TOKEN=$(docker run --rm --network $NETWORK curlimages/curl:8.7.1 \
  -X POST "$KC_URL" \
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