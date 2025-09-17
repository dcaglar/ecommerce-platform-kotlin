#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/keycloak-values-local.yaml"

helm repo add bitnami https://charts.bitnami.com/bitnami >/dev/null
helm repo update >/dev/null

# CHART 20.0.0  -> App 23.0.7
helm upgrade --install keycloak bitnami/keycloak \
  -n payment --create-namespace \
  --version 20.0.0 \
  -f "$VALUES_FILE" \
  --set global.imageRegistry=docker.io \
  --set image.registry=docker.io \
  --set image.repository=bitnamilegacy/keycloak \
  --set image.tag=23.0.7 \
  --set postgresql.enabled=true \
  --set postgresql.image.registry=docker.io \
  --set postgresql.image.repository=bitnamilegacy/postgresql \
  --set postgresql.image.tag=16.4.0-debian-12-r0


echo "🔎 Images in use:"
echo " KC: $(kubectl -n payment get sts keycloak -o jsonpath='{.spec.template.spec.containers[0].image}')"
echo " PG: $(kubectl -n payment get sts keycloak-postgresql -o jsonpath='{.spec.template.spec.containers[0].image}')"

echo "🌐 URL:"
echo "http://$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[0].address}'):$(kubectl -n payment get svc keycloak -o jsonpath='{.spec.ports[?(@.name=="http")].nodePort}')/"