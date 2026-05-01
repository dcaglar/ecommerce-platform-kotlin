#!/usr/bin/env bash
set -e

# --- config ---
PROFILE="newprofile"

# --- cleanup on exit ---
cleanup() {
  echo -e "\n🛑 Stopping resilient tunnel..."
  exit 0
}
trap cleanup INT TERM

echo "🚀 Starting Resilient Minikube Tunnel for profile: $PROFILE"
echo "💡 This script will automatically restart if the tunnel crashes during load tests."

while true; do
  echo "🧹 Cleaning up stale routes..."
  # Removed >/dev/null so you can see if it asks for a password
  sudo -E minikube -p "$PROFILE" tunnel --cleanup || true
  
  echo "▶️ Running: minikube tunnel"
  # Run in foreground. 
  if ! sudo -E minikube -p "$PROFILE" tunnel; then
    echo "⚠️ Minikube tunnel crashed or exited unexpectedly."
  fi
  
  echo "⏳ Restarting tunnel in 3 seconds..."
  sleep 3
done
