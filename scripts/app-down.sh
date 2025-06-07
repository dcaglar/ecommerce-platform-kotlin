#!/bin/bash
echo "🛑 Stopping and removing app containers only..."
docker compose -f ../docker-compose.app.yml down
echo "✅ App containers removed."
