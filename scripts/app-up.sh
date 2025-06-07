#!/bin/bash
PROFILE=${1:-docker}

echo "🚀 Starting app with SPRING_PROFILES_ACTIVE=$PROFILE..."
SPRING_PROFILES_ACTIVE=$PROFILE docker compose -f ../docker-compose.app.yml up --build -d
echo "✅ App started with profile [$PROFILE]."