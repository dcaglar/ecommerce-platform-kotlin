#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."

cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/elasticsearch-values-local.yaml"

helm repo add elastic https://helm.elastic.co --force-update
helm repo update elastic

helm upgrade --install elastic elastic/elasticsearch \
-n  logging --create-namespace \
  -f "$VALUES_FILE"
