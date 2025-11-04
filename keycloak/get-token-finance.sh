#!/bin/bash
set -e

OUTPUT_DIR="$(dirname "$0")/output"
SECRETS_FILE="${OUTPUT_DIR}/secrets.txt"
ACCESS_TOKEN_FILE="${OUTPUT_DIR}/../access-finance.token"
CLIENT_ID="finance-service"
REALM="ecommerce-platform"
KC_URL="${KC_URL:-http://keycloak:8080}"  # Or change to your forwarded port if needed

# Extract client secret
CLIENT_SECRET=$(grep FINANCE_SERVICE_CLIENT_SECRET= "$SECRETS_FILE" | cut -d= -f2 | tr -d '\r\n')

if [ -z "$CLIENT_SECRET" ]; then
  echo "❌ Could not find FINANCE_SERVICE_CLIENT_SECRET in $SECRETS_FILE"
  echo "💡 Make sure you've run ./keycloak/provision-keycloak.sh first"
  exit 1
fi

TOKEN_ENDPOINT="$KC_URL/realms/$REALM/protocol/openid-connect/token"

echo "🔐 Requesting JWT with FINANCE role from Keycloak at $TOKEN_ENDPOINT..."
RESPONSE=$(curl -s -X POST "$TOKEN_ENDPOINT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET")

ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r .access_token)

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
  echo "❌ Failed to obtain access token:"
  echo "$RESPONSE"
  exit 1
fi

echo "$ACCESS_TOKEN" > "$ACCESS_TOKEN_FILE"
echo "✅ Token saved to $ACCESS_TOKEN_FILE"
echo "💡 Use this token to query balance for any seller: GET /api/v1/sellers/{sellerId}/balance"

