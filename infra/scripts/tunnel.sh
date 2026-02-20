#!/usr/bin/env bash
set -e

# --- config ---
PROFILE="newprofile"

# --- cleanup on exit ---
cleanup() {
  echo -e "\n🛑 Stopping resilient tunnel..."
  # No need to kill minikube tunnel explicitly as it's the foreground process
  exit 0
}
trap cleanup INT TERM

echo "🚀 Starting Resilient Minikube Tunnel for profile: $PROFILE"
echo "💡 This script will automatically restart if the tunnel crashes during load tests."

while true; do
  echo "🧹 Cleaning up stale routes..."
  sudo -E minikube -p "$PROFILE" tunnel --cleanup >/dev/null 2>&1 || true
  
  echo "▶️ Running: minikube tunnel"
  # Run in foreground. If it crashes (exit code != 0), the loop continues.
  if ! sudo -E minikube -p "$PROFILE" tunnel; then
    echo "⚠️ Minikube tunnel crashed or exited unexpectedly."
  fi
  
  echo "⏳ Restarting tunnel in 3 seconds..."
  sleep 3
done
