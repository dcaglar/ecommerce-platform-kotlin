#!/opt/homebrew/bin/bash

set -euo pipefail

# --- Configurable Vars ---
KEYCLOAK_URL="http://keycloak:8080"
REALM="ecommerce-platform"
ADMIN_USER="admin"
ADMIN_PASS="adminpassword"
OUTPUT_DIR="$(dirname "$0")/output"

mkdir -p "$OUTPUT_DIR"

log() { echo "[$(date +'%H:%M:%S')] $*" >&2; }



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

# --- Create Roles ---
log "🛠️ Creating roles..."
for role in "payment:write" "FINANCE" "ADMIN" "SELLER"; do
  log "  Creating role '$role'..."
  curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/roles" \
    -H "Authorization: Bearer $KC_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"'"$role"'"}' || log "ℹ️ Role $role may already exist"
done

ROLE_NAME="payment:write"

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
  [finance-service]="FINANCE_SERVICE_CLIENT_SECRET"
)

SECRETS_OUT="$OUTPUT_DIR/secrets.txt"
echo -n > "$SECRETS_OUT"
for client in "${!CLIENTS[@]}"; do
  client_id=$(create_client "$client")
  secret=$(get_client_secret "$client_id")
  log "🔑 $client secret: $secret"
  
  # Assign payment:write to payment-service and order-service
  if [[ "$client" == "payment-service" || "$client" == "order-service" ]]; then
    assign_role_to_service_account "$client_id" "$ROLE_NAME"
  fi
  
  # Assign FINANCE role to finance-service (for balance queries)
  if [[ "$client" == "finance-service" ]]; then
    assign_role_to_service_account "$client_id" "FINANCE"
  fi
  
  echo "${CLIENTS[$client]}=$secret" >> "$SECRETS_OUT"
done

# --- Create Public Client for Password Grant (for SELLER users) ---
log "🛠️ Creating public client for password grant (seller authentication)..."
SELLER_CLIENT_ID="seller-client"
SELLER_CLIENT_KC_ID=$(get_client_id "$SELLER_CLIENT_ID")
if [[ -z "$SELLER_CLIENT_KC_ID" || "$SELLER_CLIENT_KC_ID" == "null" ]]; then
  SELLER_CLIENT_KC_ID=$(curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
    -H "Authorization: Bearer $KC_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "clientId":"'"$SELLER_CLIENT_ID"'",
      "enabled":true,
      "protocol":"openid-connect",
      "publicClient":true,
      "serviceAccountsEnabled":false,
      "directAccessGrantsEnabled":true
    }' | jq -r '.id // empty' || echo "")
  log "  ✅ Created public client: $SELLER_CLIENT_ID"
else
  log "  ℹ️ Public client $SELLER_CLIENT_ID already exists"
fi

# Configure protocol mapper for seller_id on the seller-client
if [[ -n "$SELLER_CLIENT_KC_ID" && "$SELLER_CLIENT_KC_ID" != "null" ]]; then
  log "🛠️ Configuring user attribute mapper for seller_id claim on seller-client..."
  curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/clients/$SELLER_CLIENT_KC_ID/protocol-mappers/models" \
    -H "Authorization: Bearer $KC_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "name": "seller-id-mapper",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-usermodel-attribute-mapper",
      "config": {
        "user.attribute": "seller_id",
        "claim.name": "seller_id",
        "jsonType.label": "String",
        "id.token.claim": "true",
        "access.token.claim": "true",
        "userinfo.token.claim": "true"
      }
    }' || log "ℹ️ Mapper may already exist"
fi

# --- Create Test Users (for SELLER role testing) ---
log "🛠️ Creating test users for SELLER role..."
create_test_user() {
  local username="$1"
  local seller_id="$2"
  local password="$3"
  
  log "  Creating user '$username' with seller_id=$seller_id..."
  
  # Check if user exists
  local user_id
  user_id=$(curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/users?username=$username" \
    -H "Authorization: Bearer $KC_TOKEN" | jq -r '.[0].id // empty')
  
  if [[ -z "$user_id" || "$user_id" == "null" ]]; then
    # Create user
    user_id=$(curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users" \
      -H "Authorization: Bearer $KC_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "username":"'"$username"'",
        "enabled":true,
        "credentials":[{"type":"password","value":"'"$password"'","temporary":false}],
        "attributes":{"seller_id":["'"$seller_id"'"]}
      }' | jq -r '.id // empty' || echo "")
    
    if [[ -z "$user_id" || "$user_id" == "null" ]]; then
      log "⚠️ Could not create user $username"
      return 1
    fi
    
    log "  ✅ User $username created"
  else
    log "  ℹ️ User $username already exists"
  fi
  
  # Assign SELLER role
  local role_obj
  role_obj=$(curl -sf "$KEYCLOAK_URL/admin/realms/$REALM/roles/SELLER" \
    -H "Authorization: Bearer $KC_TOKEN")
  
  if [[ -n "$role_obj" && "$role_obj" != "null" ]]; then
    curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users/$user_id/role-mappings/realm" \
      -H "Authorization: Bearer $KC_TOKEN" \
      -H "Content-Type: application/json" \
      -d "[$role_obj]" || log "  ℹ️ SELLER role may already be assigned"
    log "  ✅ SELLER role assigned to $username"
  fi
}

# Create test sellers
create_test_user "seller-111" "SELLER-111" "seller123"
create_test_user "seller-222" "SELLER-222" "seller123"
create_test_user "seller-333" "SELLER-333" "seller123"


log "🔒 Client secrets written to $SECRETS_OUT"
log "📝 Test users created:"
log "   - seller-111 / seller123 (seller_id: SELLER-111)"
log "   - seller-222 / seller123 (seller_id: SELLER-222)"
log "   - seller-333 / seller123 (seller_id: SELLER-333)"
log "🎉 Keycloak provisioning complete!"