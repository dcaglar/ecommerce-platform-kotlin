#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/monitoring-stack-values-local.yaml"


resource "kubernetes_namespace" "monitoring" {
  metadata {
    name = "monitoring"
  }
}

resource "helm_release" "prometheus_stack" {
  name       = "prometheus-stack"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  namespace  = kubernetes_namespace.monitoring.metadata[0].name
  
  # Maps to your -f "$VALUES_FILE"
  values = [
    file("$VALUES_FILE")
  ]

  wait    = true
  timeout = 600 # 10 minutes, matching your bash script
  
  depends_on = [
    azurerm_kubernetes_cluster.aks
  ]
}