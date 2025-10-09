#!/usr/bin/env bash
set -euo pipefail

PROFILE="${MINIKUBE_PROFILE:-newprofile}"
K8S_VERSION="${K8S_VERSION:-}"   # e.g. v1.29.4 (optional)
DRIVER="${MINIKUBE_DRIVER:-docker}"

# --- detect Docker Desktop resources (fallback to sensible defaults) ---
detect_docker() {
  local cpus mem_bytes
  if ! command -v docker >/dev/null 2>&1; then
    echo "!! docker not found; using defaults (cpus=6, mem=10240MB)" >&2
    echo "6 10240"
    return
  fi

  # Try Go template first (fast & stable); fallback to parsing if needed
  cpus="$(docker info --format '{{.NCPU}}' 2>/dev/null || true)"
  mem_bytes="$(docker info --format '{{.MemTotal}}' 2>/dev/null || true)"

  if [[ -z "${cpus:-}" || -z "${mem_bytes:-}" || ! "$cpus" =~ ^[0-9]+$ || ! "$mem_bytes" =~ ^[0-9]+$ ]]; then
    # Fallback parser
    cpus="$(docker system info 2>/dev/null | awk -F': ' '/CPUs/ {print $2; exit}')"
    # Mem may be like "12.00GiB"; normalize to MiB
    local mem_line
    mem_line="$(docker system info 2>/dev/null | awk -F': ' '/Total Memory/ {print $2; exit}')"
    # Convert "12.00GiB" or "12288MiB" to MiB
    if [[ "$mem_line" =~ ^([0-9.]+)[[:space:]]*Gi?B$ ]]; then
      local gib="${BASH_REMATCH[1]}"
      # 1 GiB = 1024 MiB
      mem_bytes="$(awk -v g="$gib" 'BEGIN{printf "%.0f", g*1024*1024*1024}')"
    elif [[ "$mem_line" =~ ^([0-9.]+)[[:space:]]*Mi?B$ ]]; then
      local mib="${BASH_REMATCH[1]}"
      mem_bytes="$(awk -v m="$mib" 'BEGIN{printf "%.0f", m*1024*1024}')"
    else
      # Unknown; default to ~10GiB
      cpus="${cpus:-6}"
      echo "${cpus} 10240"
      return
    fi
  fi

  # Convert MemTotal bytes -> MB (MiB)
  local mem_mb
  mem_mb="$(( mem_bytes / 1024 / 1024 ))"  # integer floor

  # Harden: if values look absurd, fallback
  [[ "$cpus" =~ ^[0-9]+$ ]] || cpus=6
  [[ "$mem_mb" =~ ^[0-9]+$ && "$mem_mb" -ge 512 ]] || mem_mb=10240

  echo "${cpus} ${mem_mb}"
}

read -r DOCKER_CPUS DOCKER_MEM_MB < <(detect_docker)

# Allow env overrides but clamp to Docker Desktop caps if driver=docker
REQ_CPUS="${MINIKUBE_CPUS:-$DOCKER_CPUS}"
REQ_MEM_MB="${MINIKUBE_MEMORY:-$DOCKER_MEM_MB}"

if [[ "$DRIVER" == "docker" ]]; then
  if (( REQ_CPUS > DOCKER_CPUS )); then
    echo "!! Requested CPUs (${REQ_CPUS}) > Docker Desktop CPUs (${DOCKER_CPUS}); clamping to ${DOCKER_CPUS}"
    REQ_CPUS="$DOCKER_CPUS"
  fi
  if (( REQ_MEM_MB > DOCKER_MEM_MB )); then
    echo "!! Requested memory (${REQ_MEM_MB}MB) > Docker Desktop memory (${DOCKER_MEM_MB}MB); clamping to ${DOCKER_MEM_MB}MB"
    REQ_MEM_MB="$DOCKER_MEM_MB"
  fi
fi

echo ">> Docker Desktop detected: CPUs=${DOCKER_CPUS}, Mem=${DOCKER_MEM_MB}MB"
echo ">> Starting minikube with:   driver=${DRIVER}, profile=${PROFILE}, cpus=${REQ_CPUS}, memory=${REQ_MEM_MB}MB"

# Build minikube start args
args=(start -p "${PROFILE}" --driver="${DRIVER}" --cpus="${REQ_CPUS}" --memory="${REQ_MEM_MB}")
[[ -n "${K8S_VERSION}" ]] && args+=("--kubernetes-version=${K8S_VERSION}")

# NOTE: We intentionally DO NOT pass kubelet system/kube-reserved here so
# kubelet allocatable ~= container limits, keeping things consistent.

if ! minikube status -p "${PROFILE}" >/dev/null 2>&1; then
  minikube "${args[@]}"
else
  echo ">> Minikube already running (profile=${PROFILE})."
fi

# Point kubectl to this profile
minikube profile "${PROFILE}" >/dev/null
kubectl config use-context "${PROFILE}" >/dev/null

echo ">> Enabling metrics-server addon"
minikube addons enable metrics-server -p "${PROFILE}" >/dev/null || true

echo ">> Waiting for metrics-server to be Available..."
kubectl -n kube-system rollout status deploy/metrics-server --timeout=180s || true

echo ">> Sanity check:"
kubectl top nodes || echo "metrics may need ~1 minute to appear"

echo "✅ Cluster ready"