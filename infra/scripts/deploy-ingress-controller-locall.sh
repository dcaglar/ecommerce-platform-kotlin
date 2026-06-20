#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"
VALUES_FILE="$REPO_ROOT/infra/helm-values/ingress-controllers-values-local.yaml"

helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true
 helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
 -n ingress-nginx --create-namespace \
  -f "$VALUES_FILE" \
