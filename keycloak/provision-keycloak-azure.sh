#!/usr/bin/env bash
set -euo pipefail

export KEYCLOAK_URL="http://20.126.207.47:8080"
echo "🎯 Connecting to Keycloak LoadBalancer at: $KEYCLOAK_URL"

echo "⏳ Waiting for Keycloak to boot up and accept connections..."
until curl -sSf "$KEYCLOAK_URL" > /dev/null 2>&1 || curl -sSf "${KEYCLOAK_URL}/realms/master" > /dev/null 2>&1; do
    sleep 5
done
echo "✅ Keycloak is online!"

bash "$(dirname "$0")/provision-keycloak.sh"
