#!/usr/bin/env bash
# minikube-nuke-dev.sh — COMPLETE reset for local dev environment
# 💣 Deletes all Minikube clusters, all Docker volumes/images/networks,
# 💣 Deletes ALL Kubernetes PVCs/PVs and namespaces,
# 💣 Deletes ~/.minikube and ~/.kube/config leftovers.

set -euo pipefail

echo "⚠️  WARNING: This will completely erase ALL Minikube & Docker data."
read -r -p "Type 'NUKE' to confirm: " CONFIRM
[[ "$CONFIRM" == "NUKE" ]] || { echo "Aborted."; exit 1; }

echo "🚀  Stopping and deleting all Minikube clusters..."
minikube delete --all --purge || true

echo "🧹  Deleting ~/.minikube and ~/.kube directories..."
rm -rf ~/.minikube ~/.kube || true

echo "🧱  Deleting ALL Kubernetes namespaces (including PVCs/PVs)..."
if command -v kubectl >/dev/null 2>&1; then
  kubectl delete pvc --all --all-namespaces || true
  kubectl delete pv --all || true
  kubectl delete ns --all || true
fi

echo "🐳  Stopping all Docker containers..."
docker stop $(docker ps -aq) 2>/dev/null || true

echo "🔥  Removing ALL Docker containers, networks, and volumes..."
docker system prune -af --volumes || true

echo "🧨  Removing leftover Kubernetes-related Docker volumes (just in case)..."
docker volume ls -q | grep -E 'kube|minikube|pvc' | xargs -r docker volume rm || true

echo "🪣  Removing dangling Docker images (if any)..."
docker image prune -af || true

echo "🧰  Removing any kind/minikube networks..."
docker network ls -q | grep -E 'minikube|kind|kube' | xargs -r docker network rm || true

echo "✅  System fully nuked. All Kubernetes and Docker data wiped."
echo "🧘  Next step: re-run your ./deploy-all.sh to start fresh."