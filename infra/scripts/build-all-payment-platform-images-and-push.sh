#!/usr/bin/env bash
set -euo pipefail

NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"


echo "Building payment-service image"
"$SCRIPT_DIR/build-and-push.sh" payment-service
echo "Built and pushed payment-service docker image to remote registry"



echo "Building payment-edge-worker image"
"$SCRIPT_DIR/build-and-push.sh" payment-edge-workers
echo "Built and pushed payment-edge-worker docker image to remote registry"


echo "Building payment-central-relay image"
"$SCRIPT_DIR/build-and-push.sh" payment-central-relay
echo "Built and pushed payment-central-relay docker image to remote registry"


echo "Building payment-consumers image"
"$SCRIPT_DIR/build-and-push.sh" payment-consumers
echo "Built and pushed payment-consumers docker image to remote registry"


