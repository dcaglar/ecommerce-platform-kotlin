#!/usr/bin/env bash
set -euo pipefail

# Env knobs (all optional)
# MINIKUBE_PROFILE    -> profile to delete (default: "minikube")
# MINIKUBE_DELETE_ALL -> if "true", delete ALL profiles and purge local state
PROFILE="${MINIKUBE_PROFILE:-minikube}"
DELETE_ALL="${MINIKUBE_DELETE_ALL:-true}"

if ! command -v minikube >/dev/null 2>&1; then
  echo "❌ minikube not found in PATH"
  exit 1
fi

if [[ "${DELETE_ALL}" == "true" ]]; then
  echo ">> Deleting ALL minikube profiles and purging local state (~/.minikube)"
  # --all: delete all profiles; --purge: remove cached files, configs, contexts
  minikube delete --all --purge || true
else
  echo ">> Deleting minikube profile '${PROFILE}' (if it exists)"
  # This exits non-zero if the profile doesn't exist; swallow it for idempotency.
  minikube delete -p "${PROFILE}" || true
fi

# If kubectl's current context points at the deleted profile, try to switch away
if command -v kubectl >/dev/null 2>&1; then
  CURRENT_CTX="$(kubectl config current-context 2>/dev/null || true)"
  if [[ "${CURRENT_CTX}" == "${PROFILE}" || "${CURRENT_CTX}" == "minikube" ]]; then
    if kubectl config get-contexts docker-desktop >/dev/null 2>&1; then
      echo ">> Switching kubectl context to 'docker-desktop'"
      kubectl config use-context docker-desktop >/dev/null
    else
      echo ">> Unsetting kubectl current-context"
      kubectl config unset current-context >/dev/null || true
    fi
  fi
fi

echo "✅ Cluster deletion complete"