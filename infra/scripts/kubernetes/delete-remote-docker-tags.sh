#!/bin/bash

# --- Configuration ---
DOCKER_USER="dcaglar1987"
REPO_NAME="payment-consumers"
TAGS_TO_KEEP=10

# Prompt for PAT token
read -s -p "Enter your Docker PAT token: " DOCKER_TOKEN
echo

# 1. Get Auth Token
echo "Authenticating..."
TOKEN=$(curl -s -H "Content-Type: application/json" -X POST -d '{"username": "'"$DOCKER_USER"'", "password": "'"$DOCKER_TOKEN"'"}' https://hub.docker.com/v2/users/login/ | jq -r .token)

if [ "$TOKEN" == "null" ]; then
    echo "Authentication failed. Check your username and token."
    exit 1
fi

# 2. Get list of tags to delete (all tags except the most recent N)
echo "Fetching tags to delete..."
TAGS_TO_DELETE=$(curl -s -H "Authorization: JWT ${TOKEN}" "https://hub.docker.com/v2/repositories/${DOCKER_USER}/${REPO_NAME}/tags/?page_size=1000" | jq -r '.results | sort_by(.last_updated) | .[:-'"$TAGS_TO_KEEP"'] | .[].name')

# 3. Loop and Delete
if [ -z "$TAGS_TO_DELETE" ]; then
    echo "No tags to delete."
else
    echo "The following tags will be deleted:"
    echo "$TAGS_TO_DELETE"
    read -p "Are you sure? y/n " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        for tag in $TAGS_TO_DELETE; do
            echo "Deleting tag: $tag"
            curl -s -X DELETE -H "Authorization: JWT ${TOKEN}" "https://hub.docker.com/v2/repositories/${DOCKER_USER}/${REPO_NAME}/tags/${tag}/"
        done
        echo "Cleanup complete."
    else
        echo "Cleanup cancelled."
    fi
fi