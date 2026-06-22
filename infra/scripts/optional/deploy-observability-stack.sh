#!/bin/bash
set -euo pipefail


# --- Location Aware ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

ES_VALUES_FILE="$REPO_ROOT/infra/helm-values/elasticsearch-values-local.yaml"
KIBANA_VALUES_FILE="$REPO_ROOT/infra/helm-values/kibana-values-local.yaml"
FB_VALUES_FILE="$REPO_ROOT/infra/helm-values/filebeat-values-local.yaml"

# --- Deploy/Upgrade Postgres ---
  # Make sure repo exists & is fresh (idempotent)
helm repo add elastic https://helm.elastic.co --force-update
helm repo update elastic

  # 1️⃣  Elasticsearch (single-node, dev‐friendly)
helm upgrade --install elastic elastic/elasticsearch \
-n  logging --create-namespace \
-f "$ES_VALUES_FILE"

  # 2️⃣  Kibana UI
helm upgrade --install kibana elastic/kibana \
-n logging \
-f "$KIBANA_VALUES_FILE"

  # 3️⃣  Filebeat DS with your custom config
helm upgrade --install filebeat elastic/filebeat \
-n logging \
-f "$FB_VALUES_FILE"