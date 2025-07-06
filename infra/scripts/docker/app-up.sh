#!/bin/bash

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  echo "Usage: $0 [profile]"
  echo "Starts the app containers using the specified Docker Compose profile (default: docker)."
  exit 0
fi

PROFILE=${1:-docker}
echo "🚀 Starting app (profile=$PROFILE)..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo "❌ Docker is not running. Please start Docker Desktop or the Docker daemon."
  exit 1
fi

DOCKER_BUILDKIT=1 docker compose -f docker-compose.app.yml --profile "$PROFILE" up --build -d
STATUS=$?
if [ $STATUS -eq 0 ]; then
  echo "✅ App is up (profile=$PROFILE)."
  docker compose -f docker-compose.app.yml ps
else
  echo "❌ Failed to start app containers."
fi