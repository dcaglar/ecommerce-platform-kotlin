#!/bin/sh
set -e -f -x

KEYCLOAK_URL="http://keycloak:8080"
REALM="ecommerce-platform"
ADMIN_USER="admin"
ADMIN_PASS="admin"

log() { echo "[$(date +'%H:%M:%S')] $*" >&2; }

log "⏳ Waiting for Keycloak to be ready..."
READY=0
for i in $(seq 1 30); do
  if curl -s "$KEYCLOAK_URL/health/ready" | grep -q UP; then
    log "✅ Keycloak is ready!"
    READY=1
    break
  fi
  log "Keycloak is not ready yet, waiting... ($i/30)"
  sleep 2
done

log "🔐 Getting admin token..."
KC_TOKEN=$(curl -s \
  -d "client_id=admin-cli" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password" \
  "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  | jq -r .access_token)

if [ -z "$KC_TOKEN" ] || [ "$KC_TOKEN" = "null" ]; then
  log "❌ Failed to get admin token. Exiting."
  exit 1
fi

log "🛠️ Creating realm (if not exists)..."
curl -sf -X POST "$KEYCLOAK_URL/admin/realms" \
  -H "Authorization: Bearer $KC_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"realm":"'"$REALM"'","enabled":true}' || log "ℹ️  Realm may already exist"

log "🛠️ Creating 'payment:write' role (if not exists)..."
curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/roles" \
  -H "Authorization: Bearer $KC_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "payment:write"}' || log "ℹ️  Role payment:write may already exist"

get_client_id() {
  CLIENT_ID=$(curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$1" \
    -H "Authorization: Bearer $KC_TOKEN" | jq -r '.[0].id')
  echo "$CLIENT_ID"
}

create_client() {
  NAME="$1"
  log "🛠️ Creating client '$NAME' (if not exists)..."
  # Try to create the client, ignore errors
  curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
    -H "Authorization: Bearer $KC_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"clientId":"'"$NAME"'","enabled":true,"protocol":"openid-connect","publicClient":false,"serviceAccountsEnabled":true,"directAccessGrantsEnabled":false}' \
    || true

  # Always fetch the client ID after attempting to create
  CLIENT_ID=$(get_client_id "$NAME")

  # Ensure client is confidential (publicClient: false)
  curl -sf -X PUT "$KEYCLOAK_URL/admin/realms/$REALM/clients/$CLIENT_ID" \
    -H "Authorization: Bearer $KC_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"publicClient": false, "serviceAccountsEnabled": true, "directAccessGrantsEnabled": false}' || true

  if [ -z "$CLIENT_ID" ] || [ "$CLIENT_ID" = "null" ]; then
    log "❌ Could not find or create client $NAME. Exiting."
    exit 2
  fi
  echo "$CLIENT_ID"
}

get_client_secret() {
  # Not all clients have a secret; must be confidential
  CLIENT_ID="$1"
  SECRET=$(curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/clients/$CLIENT_ID/client-secret" \
    -H "Authorization: Bearer $KC_TOKEN" | jq -r .value)
  if [ -z "$SECRET" ] || [ "$SECRET" = "null" ]; then
    log "❗ Could not retrieve secret for client id $CLIENT_ID"
    SECRET="(not set or client not confidential)"
  fi
  echo "$SECRET"
}

ORDER_CLIENT_ID=$(create_client "order-service")
PAYMENT_CLIENT_ID=$(create_client "payment-service")

ORDER_SECRET=$(get_client_secret "$ORDER_CLIENT_ID")
PAYMENT_SECRET=$(get_client_secret "$PAYMENT_CLIENT_ID")

log "🔑 order-service secret:    $ORDER_SECRET"
log "🔑 payment-service secret:  $PAYMENT_SECRET"

# Write secrets to a file for developer onboarding
echo "ORDER_SERVICE_CLIENT_SECRET=$ORDER_SECRET" > /output/secrets.txt
echo "PAYMENT_SERVICE_CLIENT_SECRET=$PAYMENT_SECRET" >> /output/secrets.txt
log "🔒 Client secrets written to /output/secrets.txt (on host: keycloak/secrets.txt)"

for CLIENT in order-service payment-service; do
  log "🧑‍💻 Assigning 'payment:write' role to $CLIENT service account..."
  SERVICE_ACCOUNT_ID=$(curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/users?username=service-account-$CLIENT" \
    -H "Authorization: Bearer $KC_TOKEN" | jq -r '.[0].id')
  if [ -z "$SERVICE_ACCOUNT_ID" ] || [ "$SERVICE_ACCOUNT_ID" = "null" ]; then
    log "❗ Service account user for $CLIENT not found."
    continue
  fi
  curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users/$SERVICE_ACCOUNT_ID/role-mappings/realm" \
    -H "Authorization: Bearer $KC_TOKEN" \
    -H "Content-Type: application/json" \
    -d '[{"name": "payment:write"}]' || log "ℹ️  Role may already be assigned to $CLIENT"
  log "✅ Role payment:write assigned to service-account-$CLIENT"
done

log "🎉 Keycloak provisioning complete!"