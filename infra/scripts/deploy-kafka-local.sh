#!/usr/bin/env bash
set -euo pipefail

NS="payment"
VALUES_FILE="infra/helm-values/kafka-values-local.yaml"

# Add/update Bitnami charts repo (this is the Helm *chart* repo, not OCI images)
helm repo add bitnami https://charts.bitnami.com/bitnami >/dev/null 2>&1 || true
helm repo update

# Optional wipe: pass --wipe to delete PVCs and the kraft secret if you previously created bad internals
if [[ "${1-}" == "--wipe" ]]; then
  echo "Wiping previous state (StatefulSet, PVCs, kraft secret)…"
  kubectl -n "$NS" delete statefulset kafka-controller --ignore-not-found
  kubectl -n "$NS" delete pvc -l app.kubernetes.io/instance=kafka --ignore-not-found
  kubectl -n "$NS" delete secret kafka-kraft --ignore-not-found
fi

# Install/upgrade the chart (specific, stable version)
helm upgrade --install kafka bitnami/kafka \
  --version 32.3.14 \
  -n "$NS" --create-namespace \
  -f "$VALUES_FILE"

# Wait for it to be up and show services
kubectl -n "$NS" rollout status statefulset/kafka-controller --timeout=5m
kubectl -n "$NS" get pods,svc -l app.kubernetes.io/instance=kafka -o wide