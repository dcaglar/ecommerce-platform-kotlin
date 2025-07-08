#!/bin/bash
# Usage: ./delete-all-tags.sh <dockerhub-username> <dockerhub-password> <repo>
# Example: ./delete-all-tags.sh myuser mypass payment-service

set -e

USER=$1
PASS=$2
REPO=$3

if [ -z "$USER" ] || [ -z "$PASS" ] || [ -z "$REPO" ]; then
  echo "Usage: $0 <dockerhub-username> <dockerhub-password> <repo>"
  exit 1
fi

TOKEN=$(curl -s -H "Content-Type: application/json" -X POST -d "{\"username\": \"$USER\", \"password\": \"$PASS\"}" https://hub.docker.com/v2/users/login/ | jq -r .token)

TAGS=$(curl -s -H "Authorization: JWT $TOKEN" "https://hub.docker.com/v2/repositories/$USER/$REPO/tags/?page_size=1000" | jq -r '.results[].name')

for TAG in $TAGS; do
  echo "Deleting tag: $TAG"
  curl -s -X DELETE -H "Authorization: JWT $TOKEN" "https://hub.docker.com/v2/repositories/$USER/$REPO/tags/$TAG/"
done

echo "✅ All tags deleted for $USER/$REPO"