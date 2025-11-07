#!/bin/bash
set -e

OUTPUT_DIR="$(dirname "$0")/output"
SECRETS_FILE="${OUTPUT_DIR}/secrets.txt"
REALM="ecommerce-platform"
KC_URL="${KC_URL:-http://127.0.0.1:8080}"  # Defaults to localhost (port-forwarding). Override if running inside cluster.

# Default to SELLER-111, can be overridden
SELLER_ID="${1:-SELLER-111}"

# Determine client ID based on seller ID
CLIENT_ID="merchant-api-${SELLER_ID}"

# Extract client secret from secrets file
SECRET_ENV_VAR="MERCHANT_API_${SELLER_ID}_CLIENT_SECRET"
CLIENT_SECRET=$(grep "^${SECRET_ENV_VAR}=" "$SECRETS_FILE" 2>/dev/null | cut -d= -f2 | tr -d '\r\n' || echo "")

if [ -z "$CLIENT_SECRET" ]; then
  echo "❌ Could not find ${SECRET_ENV_VAR} in $SECRETS_FILE"
  echo "💡 Make sure you've run ./keycloak/provision-keycloak.sh first"
  echo "💡 Available merchant API clients: merchant-api-SELLER-111, merchant-api-SELLER-222, merchant-api-SELLER-333"
  exit 1
fi

TOKEN_ENDPOINT="$KC_URL/realms/$REALM/protocol/openid-connect/token"
ACCESS_TOKEN_FILE="${OUTPUT_DIR}/../access-merchant-api-${SELLER_ID}.token"

echo "🔐 Requesting JWT with SELLER_API role for merchant '$SELLER_ID' from Keycloak at $TOKEN_ENDPOINT..."
RESPONSE=$(curl -s -X POST "$TOKEN_ENDPOINT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET")

ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r .access_token)

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
  echo "❌ Failed to obtain access token:"
  echo "$RESPONSE"
  echo ""
  echo "💡 Make sure you've run ./keycloak/provision-keycloak.sh first"
  echo "💡 Available merchant API clients: merchant-api-SELLER-111, merchant-api-SELLER-222, merchant-api-SELLER-333"
  exit 1
fi

# Extract seller_id from token (optional, for verification)
# Use Python for reliable JWT decoding (handles URL-safe base64 and padding)
SELLER_ID_IN_TOKEN=$(python3 -c "
import base64, json, sys
token = '$ACCESS_TOKEN'
try:
    parts = token.split('.')
    if len(parts) > 1:
        payload = parts[1]
        payload += '=' * (4 - len(payload) % 4)
        data = json.loads(base64.urlsafe_b64decode(payload))
        print(data.get('seller_id', ''))
except:
    pass
" 2>/dev/null || echo "")

if [ -n "$SELLER_ID_IN_TOKEN" ]; then
  echo "✅ Token contains seller_id: $SELLER_ID_IN_TOKEN"
  if [ "$SELLER_ID_IN_TOKEN" != "$SELLER_ID" ]; then
    echo "⚠️  Warning: Token seller_id ($SELLER_ID_IN_TOKEN) doesn't match requested ($SELLER_ID)"
  fi
else
  echo "⚠️  Note: seller_id claim not found in token. Make sure provisioning script configured the mapper."
fi

# Verify SELLER_API role is present
SELLER_API_ROLE=$(python3 -c "
import base64, json, sys
token = '$ACCESS_TOKEN'
try:
    parts = token.split('.')
    if len(parts) > 1:
        payload = parts[1]
        payload += '=' * (4 - len(payload) % 4)
        data = json.loads(base64.urlsafe_b64decode(payload))
        roles = data.get('realm_access', {}).get('roles', [])
        if 'SELLER_API' in roles:
            print('SELLER_API')
except:
    pass
" 2>/dev/null || echo "")

if [ -n "$SELLER_API_ROLE" ]; then
  echo "✅ Token contains SELLER_API role"
else
  echo "⚠️  Warning: SELLER_API role not found in token. Make sure role was assigned to service account."
fi

echo "$ACCESS_TOKEN" > "$ACCESS_TOKEN_FILE"
echo "✅ Token saved to $ACCESS_TOKEN_FILE"
echo "💡 Use this token to query balance via merchant API: GET /api/v1/sellers/me/balance"
echo "💡 This token uses Client Credentials flow (M2M) with SELLER_API role"

