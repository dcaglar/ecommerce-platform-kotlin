set -ex

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && cd ../.. && pwd)"


#!/bin/bash
set -e

# Kill all kubectl port-forward processes
PORT_FORWARD_PIDS=$(pgrep -f "kubectl port-forward")
if [ -n "$PORT_FORWARD_PIDS" ]; then
  echo "Killing kubectl port-forward processes: $PORT_FORWARD_PIDS"
  kill $PORT_FORWARD_PIDS || true
else
  echo "No kubectl port-forward processes found."
fi

NAMESPACE="payment"

# Uninstall Helm releases, ignore errors if not present
helm uninstall payment-service -n $NAMESPACE || true
helm uninstall payment-consumers -n $NAMESPACE || true
helm uninstall kafka -n $NAMESPACE || true
helm uninstall redis -n $NAMESPACE || true

# Delete app DB credentials job
kubectl delete jobs create-app-db-users -n $NAMESPACE || true

helm uninstall payment-db -n $NAMESPACE || true
helm uninstall keycloak -n $NAMESPACE || true
helm uninstall payment-platform-config -n $NAMESPACE || true

echo "✅ All Helm releases deleted!"
echo "✅ Deleting pvcs"
kubectl delete pvc --all -n $NAMESPACE
echo "✅ Deleted pvcs"


