#!/bin/bash

#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."

cd "$REPO_ROOT"
helm upgrade --install payment-consumers "$REPO_ROOT/charts/payment-consumers" \
  -n payment --create-namespace \
  -f "$REPO_ROOT/infra/helm-values/payment-consumers-values-local.yaml"