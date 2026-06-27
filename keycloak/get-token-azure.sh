#!/usr/bin/env bash
set -euo pipefail

KEYCLOAK_URL="http://20.126.207.47:8080"
bash "$(dirname "$0")/get-token.sh" "$KEYCLOAK_URL" "$@"
