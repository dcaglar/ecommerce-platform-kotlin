#!/bin/bash

echo "🧹 Stopping and removing app containers, networks, and volumes..."
docker compose -f docker-compose.app.yml down --volumes --remove-orphans
echo "✅ App cleanup complete."