#!/bin/bash
read -p "⚠️ Are you sure you want to stop and clean up infra (containers + volumes)? (y/n): " confirm
if [[ "$confirm" =~ ^[Yy]$ ]]; then
    echo "🧹 Cleaning infra containers, volumes, and orphans..."
    docker compose -f ../docker-compose.infra.yml down --volumes --remove-orphans
    echo "✅ Infra cleanup complete."
else
    echo "❌ Aborted by user."
fi
