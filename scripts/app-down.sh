#!/bin/bash
echo "🛑 Stopping app containers..."
docker compose -f ../docker-compose.app.yml down
echo "✅ App containers stopped."
