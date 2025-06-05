#!/bin/bash

echo "🔁 Running full system reset..."

./scripts/app-cleanup.sh
./scripts/infra-cleanup.sh

./scripts/infra-up.sh &

echo "⏳ Waiting for infra to stabilize..."
sleep 20  # optional delay for DB/Kafka/Keycloak

./scripts/app-up.sh