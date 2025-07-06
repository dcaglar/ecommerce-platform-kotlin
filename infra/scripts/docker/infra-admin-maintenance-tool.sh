#!/bin/bash
set -e

# Infra Admin Maintenance Tool
# Provides backup, restore, start, stop, and purge operations for infra containers and named volumes.
# Usage: Run and follow the menu. All actions require Docker to be running.

# List all data volumes here (add more as you add services)
VOLUMES=(
  grafana-data
  prometheus-data
  esdata
  keycloak_postgres_data
  payment_postgres_data
  kafka-data
  zookeeper-data
  redis-data
)

BACKUP_DIR=~/docker-backups
COMPOSE_FILE=docker-compose.infra.yml

function check_docker() {
  if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop or the Docker daemon."
    exit 1
  fi
}

function backup_data() {
  check_docker
  echo "🗄  Backing up volumes: ${VOLUMES[*]}"
  mkdir -p "$BACKUP_DIR"
  for vol in "${VOLUMES[@]}"; do
    echo "   ⏳ $vol ..."
    if docker volume inspect "$vol" >/dev/null 2>&1; then
      docker run --rm -v $vol:/data -v $BACKUP_DIR:/backup alpine tar czf /backup/${vol}-backup.tar.gz -C /data .
      if [ $? -eq 0 ]; then
        echo "   ✅ $vol backup complete."
      else
        echo "   ❌ $vol backup failed."
      fi
    else
      echo "   ⚠️  Volume $vol does not exist, skipping."
    fi
  done
  echo "✅ All backups saved to $BACKUP_DIR"
}

function restore_data() {
  check_docker
  echo "🔄  Restoring volumes from $BACKUP_DIR..."
  for vol in "${VOLUMES[@]}"; do
    BACKUP_FILE="$BACKUP_DIR/${vol}-backup.tar.gz"
    if [ -f "$BACKUP_FILE" ]; then
      echo "   ⏳ $vol ..."
      docker run --rm -v $vol:/data -v $BACKUP_DIR:/backup alpine sh -c "cd /data && tar xzf /backup/${vol}-backup.tar.gz"
      if [ $? -eq 0 ]; then
        echo "   ✅ $vol restore complete."
      else
        echo "   ❌ $vol restore failed."
      fi
    else
      echo "   ⚠️  No backup found for $vol, skipping."
    fi
  done
  echo "✅ Restore process finished."
}

function start_infra() {
  check_docker
  echo "🚀 Starting infra containers..."
  docker compose -f $COMPOSE_FILE up -d
  if [ $? -eq 0 ]; then
    echo "✅ Infra is up."
    docker compose -f $COMPOSE_FILE ps
  else
    echo "❌ Failed to start infra containers."
  fi
}

function stop_infra() {
  check_docker
  echo "🛑 Stopping infra containers..."
  docker compose -f $COMPOSE_FILE down
  if [ $? -eq 0 ]; then
    echo "✅ Infra stopped. Data volumes remain."
    docker compose -f $COMPOSE_FILE ps
  else
    echo "❌ Failed to stop infra containers."
  fi
}

function purge_infra() {
  check_docker
  echo "⚠️ This will delete all infra containers AND these volumes:"
  for vol in "${VOLUMES[@]}"; do
    echo "   - $vol"
  done
  read -p "Are you sure? This CANNOT be undone. Type 'yes' to confirm: " confirm
  if [ "$confirm" = "yes" ]; then
    docker compose -f $COMPOSE_FILE down -v
    for vol in "${VOLUMES[@]}"; do
      if docker volume inspect "$vol" >/dev/null 2>&1; then
        docker volume rm "$vol"
      fi
    done
    echo "✅ Infra containers and volumes purged."
  else
    echo "❌ Aborted."
  fi
}

function menu() {
  echo "\nInfra Admin Maintenance Tool"
  echo "Choose an option:"
  echo "1. Start infra"
  echo "2. Stop infra (keep data)"
  echo "3. Purge infra (delete ALL data/volumes)"
  echo "4. Backup all volumes"
  echo "5. Restore all volumes"
  echo "6. Exit"
  read -p "Enter [1-6]: " choice
  case $choice in
    1) start_infra ;;
    2) stop_infra ;;
    3) purge_infra ;;
    4) backup_data ;;
    5) restore_data ;;
    6) exit 0 ;;
    *) echo "Invalid option"; menu ;;
  esac
}

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
  echo "Infra Admin Maintenance Tool"
  echo "Backup, restore, start, stop, and purge infra containers and volumes."
  echo "Run and follow the menu."
  exit 0
fi

menu