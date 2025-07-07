#!/opt/homebrew/bin/bash

set -euo pipefail

# --- Configurable Vars ---
KEYCLOAK_URL="http://keycloak:8080"
REALM="ecommerce-platform"
ADMIN_USER="admin"
ADMIN_PASS="admin"
OUTPUT_DIR="$(dirname "$0")/output"

mkdir -p "$OUTPUT_DIR"

log() { echo "[$(date +'%H:%M:%S')] $*" >&2; }

# --- Wait for Keycloak to be Ready ---
log "⏳ Waiting for Keycloak to be ready at $KEYCLOAK_URL..."
for i in $(seq 1 30); do
  if curl -s "$KEYCLOAK_URL/health/ready" | grep -q UP; then
    log "✅ Keycloak is ready!"
    break
  fi
  log "Keycloak not ready yet, waiting ($i/30)..."
  sleep 2
done

# --- Authenticate as Admin ---
log "🔐 Authenticating as admin..."
KC_TOKEN=$(curl -s \
  -d "client_id=admin-cli" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password" \
  "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  | jq -r .access_token)

if [[ -z "$KC_TOKEN" || "$KC_TOKEN" == "null" ]]; then
  log "❌ Failed to get admin token. Exiting."
  exit 1
fi

# --- Create Realm ---
log "🛠️ Ensuring realm '$REALM' exists..."
curl -sf -X POST "$KEYCLOAK_URL/admin/realms" \
  -H "Authorization: Bearer $KC_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"realm":"'"$REALM"'","enabled":true}' || log "ℹ️ Realm may already exist"

# --- Create Role ---
ROLE_NAME="payment:write"
log "🛠️ Ensuring role '$ROLE_NAME' exists..."
curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/roles" \
  -H "Authorization: Bearer $KC_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"'"$ROLE_NAME"'"}' || log "ℹ️ Role may already exist"

# --- Functions for Client Management ---
get_client_id() {
  local name="$1"
  curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$name" \
    -H "Authorization: Bearer $KC_TOKEN" | jq -r '.[0].id'
}

create_client() {
  local name="$1"
  log "🛠️ Ensuring client '$name' exists..."
  curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
    -H "Authorization: Bearer $KC_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"clientId":"'"$name"'","enabled":true,"protocol":"openid-connect","publicClient":false,"serviceAccountsEnabled":true,"directAccessGrantsEnabled":false}' \
    || true
  local client_id
  client_id=$(get_client_id "$name")
  if [[ -z "$client_id" || "$client_id" == "null" ]]; then
    log "❌ Could not find or create client $name. Exiting."
    exit 2
  fi
  # Set client to confidential and set long token lifespan
  curl -sf -X PUT "$KEYCLOAK_URL/admin/realms/$REALM/clients/$client_id" \
    -H "Authorization: Bearer $KC_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"publicClient":false,"serviceAccountsEnabled":true,"directAccessGrantsEnabled":false,"attributes":{"access.token.lifespan":"2592000"}}' || true
  echo "$client_id"
}

get_client_secret() {
  local client_id="$1"
  curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/clients/$client_id/client-secret" \
    -H "Authorization: Bearer $KC_TOKEN" | jq -r .value
}

assign_role_to_service_account() {
  local client_id="$1"
  local role_name="$2"
  # Get service account user id
  local sa_id
  sa_id=$(curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/clients/$client_id/service-account-user" \
    -H "Authorization: Bearer $KC_TOKEN" | jq -r .id)
  if [[ -z "$sa_id" || "$sa_id" == "null" ]]; then
    log "❗ Service account user for client $client_id not found."
    return 1
  fi
  # Get role as object
  local role_obj
  role_obj=$(curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/roles/$role_name" \
    -H "Authorization: Bearer $KC_TOKEN")
  if [[ -z "$role_obj" || "$role_obj" == "null" ]]; then
    log "❗ Role $role_name not found."
    return 1
  fi
  # Assign role
  curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users/$sa_id/role-mappings/realm" \
    -H "Authorization: Bearer $KC_TOKEN" \
    -H "Content-Type: application/json" \
    -d "[$role_obj]" || log "ℹ️ Role $role_name may already be assigned"
  log "✅ Role $role_name assigned to service-account-$client_id"
}

# --- Provision Clients & Roles ---
declare -A CLIENTS=(
  [order-service]="ORDER_SERVICE_CLIENT_SECRET"
  [payment-service]="PAYMENT_SERVICE_CLIENT_SECRET"
)

SECRETS_OUT="$OUTPUT_DIR/secrets.txt"
echo -n > "$SECRETS_OUT"
for client in "${!CLIENTS[@]}"; do
  client_id=$(create_client "$client")
  secret=$(get_client_secret "$client_id")
  log "🔑 $client secret: $secret"
  assign_role_to_service_account "$client_id" "$ROLE_NAME"
  echo "${CLIENTS[$client]}=$secret" >> "$SECRETS_OUT"
done

log "🔒 Client secrets written to $SECRETS_OUT"
log "🎉 Keycloak provisioning complete!"