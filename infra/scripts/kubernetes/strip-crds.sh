#!/usr/bin/env bash
set -euo pipefail

BASE="https://raw.githubusercontent.com/prometheus-operator/prometheus-operator/main/example/prometheus-operator-crd"
CRDS=( alertmanagers prometheusagents prometheuses thanosrulers )

trim() {                     # drop ONLY the huge kubectl annotation
  awk '
    /^[[:space:]]+kubectl.kubernetes.io\/last-applied-configuration:/ {skip=1; next}
    skip && $0 !~ /^[[:space:]]/ {skip=0}
    !skip
  '
}

for crd in "${CRDS[@]}"; do
  echo "↪  installing ${crd}.monitoring.coreos.com …"
  kubectl delete crd "${crd}.monitoring.coreos.com" --ignore-not-found || true

  curl -sL "${BASE}/monitoring.coreos.com_${crd}.yaml" \
    | trim \
    | kubectl apply --server-side -f -
done