#!/usr/bin/env bash

 set -euo pipefail

 MEMORY="${MINIKUBE_MEMORY:-10000}"   # MB
 CPUS="${MINIKUBE_CPUS:-6}"
 K8S_VERSION="${K8S_VERSION:-}"       # e.g. v1.29.4 (optional)

 echo ">> Starting minikube (memory=${MEMORY}MB, cpus=${CPUS})"
 if ! minikube status >/dev/null 2>&1; then
   args=(start --memory="${MEMORY}" --cpus="${CPUS}")
   [[ -n "${K8S_VERSION}" ]] && args+=("--kubernetes-version=${K8S_VERSION}")
   minikube "${args[@]}"
 else
   echo ">> Minikube already running."
 fi

 echo ">> Enabling metrics-server addon"
 minikube addons enable metrics-server >/dev/null || true

 echo ">> Waiting for metrics-server to be Available..."
 kubectl -n kube-system rollout status deploy/metrics-server --timeout=180s || true

 echo ">> Sanity check:"
 kubectl top nodes || echo "metrics may need ~1 minute to appear"



 echo "✅ Cluster ready"