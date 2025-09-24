#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"


VALUES_FILE="$REPO_ROOT/infra/helm-values/payment-db-values-local.yaml"

# You can override PG_TAG when calling the script
PG_TAG="${PG_TAG:-16.4.0-debian-12-r0}"

# Fail fast if the tag doesn't exist (prevents ImagePullBackOff surprises)
if ! docker manifest inspect "docker.io/bitnamilegacy/postgresql:${PG_TAG}" >/dev/null 2>&1; then
  echo "❌ Tag not found: bitnamilegacy/postgresql:${PG_TAG}"
  echo "   Try another legacy tag you've verified with: docker manifest inspect docker.io/bitnamilegacy/postgresql:<tag>"
  exit 1
fi

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm upgrade --install payment-db bitnami/postgresql \
  -n payment --create-namespace \
  --version 15.5.1 \
  -f "$VALUES_FILE" \
  --set image.tag="${PG_TAG}"

echo "✅ payment-db deployed (image=bitnamilegacy/postgresql:${PG_TAG})"