#!/usr/bin/env bash
# orbstack-nuke-dev.sh — COMPLETE reset for local dev environment on OrbStack
# 💣 Deletes ALL Kubernetes PVCs/PVs and namespaces using native kubectl
# 💣 Deletes all Docker volumes/images/networks
# 💣 Does NOT touch Minikube (as OrbStack provides the native cluster)

set -euo pipefail

echo "🛡️  Checking Kubernetes context..."
CURRENT_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "none")
if [[ "$CURRENT_CONTEXT" != "orbstack" ]]; then
  echo "⚠️  Current context is '$CURRENT_CONTEXT', but this script requires 'orbstack'."
  if kubectl config get-contexts orbstack >/dev/null 2>&1; then
    echo "🔄 Switching context to 'orbstack'..."
    kubectl config use-context orbstack
  else
    echo "❌ OrbStack context not found! Is OrbStack running with Kubernetes enabled?"
    exit 1
  fi
fi


echo "⚠️  WARNING: This will completely erase ALL OrbStack Kubernetes & Docker data."
read -r -p "Type 'NUKE' to confirm: " CONFIRM
[[ "$CONFIRM" == "NUKE" ]] || { echo "Aborted."; exit 1; }

echo "🚀  Deleting all Helm releases in the 'payment', 'monitoring', and 'ingress-nginx' namespaces..."
if command -v helm >/dev/null 2>&1; then
  helm ls -a -n payment -q | xargs -r helm uninstall -n payment || true
    helm ls -a -n monitoring -q | xargs -r helm uninstall -n monitoring || true
  helm ls -a -n ingress-nginx -q | xargs -r helm uninstall -n ingress-nginx || true
fi

echo "⏳  Waiting for pods to terminate gracefully..."
if command -v kubectl >/dev/null 2>&1; then
  kubectl wait --for=delete pod --all -n payment --timeout=60s 2>/dev/null || true
  kubectl wait --for=delete pod --all -n monitoring --timeout=60s 2>/dev/null || true
  kubectl wait --for=delete pod --all -n ingress-nginx --timeout=60s 2>/dev/null || true

  echo "🧹  Deleting PVCs gracefully to allow local-path-provisioner to clean physical data..."
  kubectl delete pvc --all -n payment --timeout=30s 2>/dev/null || true
  kubectl delete pvc --all -n monitoring --timeout=30s 2>/dev/null || true
  kubectl delete pvc --all -n ingress-nginx --timeout=30s 2>/dev/null || true

  echo "🧹  Patching out finalizers from any remaining PVCs to prevent hanging..."
  kubectl get pvc --all-namespaces -o custom-columns=NS:.metadata.namespace,NAME:.metadata.name --no-headers 2>/dev/null | while read -r ns name; do
    kubectl patch pvc "$name" -n "$ns" -p '{"metadata":{"finalizers":null}}' --type=merge 2>/dev/null || true
  done

  echo "🧹  Patching out finalizers from any remaining PVs..."
  kubectl get pv -o name 2>/dev/null | while read -r pv; do
    kubectl patch "$pv" -p '{"metadata":{"finalizers":null}}' --type=merge 2>/dev/null || true
  done

  echo "🧨  Force deleting any stuck PVCs and PVs..."
  kubectl delete pvc --all --all-namespaces --force --grace-period=0 2>/dev/null || true
  kubectl delete pv --all --force --grace-period=0 2>/dev/null || true

  echo "💣  Force deleting namespaces..."
  kubectl delete ns payment --force --grace-period=0 2>/dev/null || true
  kubectl delete ns monitoring --force --grace-period=0 2>/dev/null || true
  kubectl delete ns ingress-nginx --force --grace-period=0 2>/dev/null || true
fi

echo "🐳  Stopping all Docker containers..."
docker stop $(docker ps -aq) 2>/dev/null || true

echo "🔥  Removing ALL Docker containers, networks, and volumes..."
docker system prune -af --volumes || true

echo "🧨  Removing leftover Kubernetes-related Docker volumes (just in case)..."
docker volume ls -q | grep -E 'kube|pvc' | xargs -r docker volume rm || true

echo "🪣  Removing dangling Docker images (if any)..."
docker image prune -af || true

echo "🧰  Removing any custom bridge networks..."
docker network ls -q | grep -E 'kube' | xargs -r docker network rm || true

echo "✅  System fully nuked. All Kubernetes and Docker data wiped."
echo "🧘  Next step: re-run your ./infra/scripts/deploy-all-local.sh to start fresh."
