#!/usr/bin/env bash
set -euo pipefail

KEYCLOAK_IP=$(kubectl get svc -n ingress-nginx ingress-nginx-controller -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
KEYCLOAK_URL="http://${KEYCLOAK_IP}/auth"
bash "$(dirname "$0")/get-token.sh" "$KEYCLOAK_URL" "$@"
