#!/bin/bash
echo "🧨 Performing full system reset..."
../app-down.sh
../infra-down.sh
echo "🧹 Removing volumes and unused networks..."
docker volume prune -f
docker network prune -f
echo "✅ System fully reset."
