#!/usr/bin/env bash
set -euo pipefail

PROFILE="${MINIKUBE_PROFILE:-payments}"
MEMORY="${MINIKUBE_MEMORY:-10000}"
CPUS="${MINIKUBE_CPUS:-6}"
K8S_VERSION="${K8S_VERSION:-}"   # e.g. v1.29.4 (optional)

echo ">> Starting minikube profile: $PROFILE (memory=${MEMORY}MB, cpus=${CPUS})"
if minikube profile list -o json 2>/dev/null | grep -q "\"Name\": \"${PROFILE}\""; then
  minikube -p "$PROFILE" status >/dev/null || \
  minikube -p "$PROFILE" start --memory="$MEMORY" --cpus="$CPUS" ${K8S_VERSION:+--kubernetes-version="$K8S_VERSION"}
else
  minikube -p "$PROFILE" start --memory="$MEMORY" --cpus="$CPUS" ${K8S_VERSION:+--kubernetes-version="$K8S_VERSION"}
fi

echo ">> Enabling metrics-server addon"
minikube -p "$PROFILE" addons enable metrics-server >/dev/null

# Optional useful addons (uncomment if you want them)
# minikube -p "$PROFILE" addons enable ingress
# minikube -p "$PROFILE" addons enable dashboard

echo ">> Waiting for metrics-server to become available..."
kubectl -n kube-system wait deploy/metrics-server --for=condition=Available --timeout=120s || true

echo ">> Sanity check:"
kubectl top nodes || echo "metrics may take ~1 min to appear"

echo "✅ Cluster ready (profile: $PROFILE)"