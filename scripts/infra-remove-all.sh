#!/bin/bash

echo "🛑 1. Stopping all running containers..."
docker ps -aq | xargs docker stop 2>/dev/null

echo "🧹 2. Removing all containers..."
docker ps -aq | xargs docker rm -f 2>/dev/null

echo "🌐 3. Removing all user-defined Docker networks..."
docker network ls --filter type=custom -q | xargs docker network rm 2>/dev/null

echo "🗑️ 4. Removing local images related to 'ecommerce-platform-kotlin'..."
docker images | grep ecommerce-platform-kotlin | awk '{print $3}' | xargs docker rmi -f 2>/dev/null

# Uncomment this to remove all local images (CAUTION!)
# echo "⚠️ Removing ALL local Docker images..."
# docker images -q | xargs docker rmi -f

echo "📦 5. Removing Docker volumes for databases and observability stack..."
docker volume rm payment_postgres_data keycloak_postgres_data elasticsearch-data filebeat-data 2>/dev/null

# Optional: Prune ALL unused volumes (dangerous if you use Docker elsewhere)
# docker volume prune -f

echo "✅ Docker cleanup completed."
echo "📋 You can now run docker-compose up --build from a clean slate."


#  rembember to run rhis chmod +x infra-remove-all.sh