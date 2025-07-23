#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."

cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/keycloak-values-local.yaml"

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm upgrade --install keycloak bitnami/keycloak \
  -n payment --create-namespace \
  --version 21.2.0 \
  -f "$VALUES_FILE"