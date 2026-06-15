#!/bin/bash
set -e

echo "Building docker images..."
cd /Users/dogancaglar/IdeaProjects/ecommerce-platform-kotlin
docker build -f payment-service/Dockerfile -t "dcaglar1987/payment-service:latest" . &
docker build -f payment-edge-workers/Dockerfile -t "dcaglar1987/payment-edge-workers:latest" . &
docker build -f payment-consumers/Dockerfile -t "dcaglar1987/payment-consumers:latest" . &
docker build -f payment-central-relay/Dockerfile -t "dcaglar1987/payment-central-relay:latest" . &
wait

echo "Deploying infra..."
./infra/scripts/deploy-all-local.sh

echo "Waiting for DB to start..."
sleep 20

echo "Deploying apps..."
./infra/scripts/deploy-payment-service-local.sh || true
./infra/scripts/deploy-payment-edge-cell-local.sh || true
./infra/scripts/deploy-payment-consumers-local.sh || true
./infra/scripts/deploy-payment-central-relay-local.sh || true

echo "Done!"
