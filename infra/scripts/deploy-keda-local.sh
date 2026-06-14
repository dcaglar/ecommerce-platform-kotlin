#!/usr/bin/env bash
set -euo pipefail

echo "▶️  Deploying KEDA (Kubernetes Event-driven Autoscaling) locally"
helm repo add kedacore https://kedacore.github.io/charts >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true

# Install KEDA into the keda namespace
helm upgrade --install keda kedacore/keda \
  --namespace keda --create-namespace \
  --wait --timeout 5m

echo "✅ KEDA deployed successfully locally."
