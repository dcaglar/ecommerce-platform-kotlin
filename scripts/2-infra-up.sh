#!/bin/bash

echo "🚀 Starting infrastructure containers..."
docker compose -f docker-compose.infra.yml up --build