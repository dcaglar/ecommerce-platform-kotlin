#!/bin/bash

set -e

HOSTS_LINE1="127.0.0.1 payment.local"
HOSTS_LINE2="127.0.0.1 keycloak"
HOSTS_FILE="/etc/hosts"

add_host_entry() {
  local entry="$1"
  if ! grep -qF "$entry" "$HOSTS_FILE"; then
    echo "Adding '$entry' to $HOSTS_FILE"
    echo "$entry" | sudo tee -a "$HOSTS_FILE" > /dev/null
  else
    echo "'$entry' already exists in $HOSTS_FILE"
  fi
}

add_host_entry "$HOSTS_LINE1"
add_host_entry "$HOSTS_LINE2"

echo "Done."

