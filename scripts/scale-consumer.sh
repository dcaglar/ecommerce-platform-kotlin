#!/bin/bash

# Usage info
if [[ "$1" == "-h" || "$1" == "--help" || -z "$1" ]]; then
  echo "Usage: $0 <replica-count>|--up|--down"
  echo "Scales the payment-consumers service to the specified number of instances, or increments/decrements by 1."
  exit 0
fi

# Get current replica count
grep_running() {
  docker compose -f ../docker-compose.app.yml ps --format json | grep -c 'payment-consumers'
}

get_current_count() {
  count=$(grep_running)
  if [[ -z "$count" || "$count" -eq 0 ]]; then
    echo 1
  else
    echo $count
  fi
}

REPLICAS=""
if [[ "$1" == "--up" ]]; then
  current=$(get_current_count)
  REPLICAS=$((current+1))
elif [[ "$1" == "--down" ]]; then
  current=$(get_current_count)
  if [[ $current -le 1 ]]; then
    echo "❌ Already at minimum (1 instance)."
    exit 1
  fi
  REPLICAS=$((current-1))
else
  REPLICAS=$1
fi

if ! [[ $REPLICAS =~ ^[0-9]+$ ]]; then
  echo "❌ Error: Replica count must be a positive integer."
  exit 1
fi

echo "🔄 Scaling payment-consumers to $REPLICAS instance(s)..."
docker compose -f ../docker-compose.app.yml up -d --scale payment-consumers=$REPLICAS
STATUS=$?
if [ $STATUS -eq 0 ]; then
  echo "✅ payment-consumers scaled to $REPLICAS instance(s)."
  docker compose -f ../docker-compose.app.yml ps | grep payment-consumers
else
  echo "❌ Failed to scale payment-consumers."
fi
