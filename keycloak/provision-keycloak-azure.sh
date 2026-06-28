#!/usr/bin/env bash
set -euo pipefail

export KEYCLOAK_IP=$(kubectl get svc -n ingress-nginx ingress-nginx-controller -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
export KEYCLOAK_URL="http://${KEYCLOAK_IP}/auth"
echo "🎯 Connecting to Keycloak LoadBalancer at: $KEYCLOAK_URL"

echo "⏳ Waiting for Keycloak to boot up and accept connections..."
until curl -sSf "$KEYCLOAK_URL" > /dev/null 2>&1 || curl -sSf "${KEYCLOAK_URL}/realms/master" > /dev/null 2>&1; do
    sleep 5
done
echo "✅ Keycloak is online!"

bash "$(dirname "$0")/provision-keycloak.sh"
