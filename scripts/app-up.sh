#!/bin/bash
PROFILE=${1:-local}
echo "🚀 Starting app (profile=$PROFILE)..."
docker compose -f ../docker-compose.app.yml --profile "$PROFILE" up --build -d
echo "✅ App is up (profile=$PROFILE)."
