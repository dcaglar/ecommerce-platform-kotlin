#!/bin/bash
set -e

# --- Help functionality ---
function print_help() {
  echo "Usage: $0 [ENV] [COMPONENT] [NAMESPACE]"
  echo "  ENV        : Target environment (default: local)"
  echo "  COMPONENT  : Component to deploy (default: all)"
  echo "  NAMESPACE  : Kubernetes namespace (default: payment)"
  echo ""
  echo "Examples:"
  echo "  $0 local payment-consumers payment"
  echo "  $0 prod all prod-ns"
  echo ""
  exit 0
}

if [[ $1 == "-h" || $1 == "--help" ]]; then
  print_help
fi
# --- Location awareness ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../../.."
cd "$REPO_ROOT"

ENV=${1:-local}
COMPONENT=${2:-all}
NS=${3:-payment}

OVERLAY="$REPO_ROOT/infra/k8s/overlays/$ENV/$COMPONENT"

echo ""
echo "----------------------------------------"
echo "🔍  SCRIPT_DIR:   $SCRIPT_DIR"
echo "🔍  REPO_ROOT:    $REPO_ROOT"
echo "🔍  ENV:          $ENV"
echo "🔍  COMPONENT:    $COMPONENT"
echo "🔍  NS:           $NS"
echo "🔍  OVERLAY:      $OVERLAY"
echo "----------------------------------------"
echo ""

if [ ! -d "$OVERLAY" ]; then
  echo "❌ Overlay path does not exist: $OVERLAY"
  exit 2
fi

echo "🚀 Deploying overlay: $OVERLAY (namespace: $NS)..."

if [[ "$COMPONENT" == "monitoring" || "$COMPONENT" == "all" ]]; then
  kubectl apply -k "$OVERLAY"              # ← let each YAML set its own ns
else
  kubectl apply -k "$OVERLAY" -n "$NS"
fi

# Optional: Apply any overlay secrets yaml files
if [ -d "$OVERLAY/secrets" ]; then
  for s in "$OVERLAY"/secrets/*.yaml; do
    [ -f "$s" ] && echo "🔐 Applying secret: $s" && kubectl apply -f "$s" -n "$NS"
  done
fi


echo ""
echo "✅ Deployed: $OVERLAY to namespace $NS"