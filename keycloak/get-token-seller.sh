#!/bin/bash
set -e

OUTPUT_DIR="$(dirname "$0")/output"
ACCESS_TOKEN_FILE="${OUTPUT_DIR}/../access-seller.token"
REALM="ecommerce-platform"
KC_URL="${KC_URL:-http://keycloak:8080}"  # Or change to your forwarded port if needed

# Default to seller-111, can be overridden
USERNAME="${1:-seller-111}"
PASSWORD="${2:-seller123}"

TOKEN_ENDPOINT="$KC_URL/realms/$REALM/protocol/openid-connect/token"
CLIENT_ID="seller-client"  # Public client created by provisioning script

echo "🔐 Requesting JWT with SELLER role for user '$USERNAME' from Keycloak at $TOKEN_ENDPOINT..."
RESPONSE=$(curl -s -X POST "$TOKEN_ENDPOINT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=$CLIENT_ID" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD")

ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r .access_token)

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
  echo "❌ Failed to obtain access token:"
  echo "$RESPONSE"
  echo ""
  echo "💡 Make sure you've run ./keycloak/provision-keycloak.sh first"
  echo "💡 Available test users: seller-111, seller-222, seller-333 (password: seller123)"
  exit 1
fi

# Extract seller_id from token (optional, for verification)
# Decode JWT payload (second part)
SELLER_ID=$(echo "$ACCESS_TOKEN" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq -r '.seller_id // empty' 2>/dev/null || echo "")
if [ -n "$SELLER_ID" ]; then
  echo "✅ Token contains seller_id: $SELLER_ID"
else
  echo "⚠️  Note: seller_id claim not found in token. Make sure provisioning script configured the mapper."
fi

echo "$ACCESS_TOKEN" > "$ACCESS_TOKEN_FILE"
echo "✅ Token saved to $ACCESS_TOKEN_FILE"
echo "💡 Use this token to query your own balance: GET /api/v1/sellers/me/balance"

