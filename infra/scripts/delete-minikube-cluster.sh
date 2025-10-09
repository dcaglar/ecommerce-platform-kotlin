#!/usr/bin/env bash
# minikube-nuke.sh — fully remove Minikube and leftovers (Docker driver)
# Usage:
#   ./minikube-nuke.sh           # interactive confirm
#   ./minikube-nuke.sh --dry-run # show what would be deleted
#   ./minikube-nuke.sh --force   # no prompts

set -euo pipefail

DRY_RUN=0
FORCE=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --force)   FORCE=1 ;;
    *) echo "Unknown arg: $arg" >&2; exit 1 ;;
  esac
done

run() {
  if (( DRY_RUN )); then
    echo "[dry-run] $*"
  else
    eval "$@"
  fi
}

confirm() {
  if (( FORCE )); then return 0; fi
  read -r -p "$1 [y/N] " ans
  [[ "${ans:-}" =~ ^[Yy]$ ]]
}

echo ">>> Detecting minikube CLI..."
if ! command -v minikube >/dev/null 2>&1; then
  echo "minikube not found; continuing with Docker & kubeconfig cleanup only."
fi

echo ">>> Detecting docker CLI..."
if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found; skipping Docker cleanup."
fi

echo ">>> Detecting kubectl CLI..."
if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl not found; kubeconfig cleanup will be best-effort."
fi

echo
echo "This will remove:"
echo "  • All minikube profiles & clusters"
echo "  • ~/.minikube directory"
echo "  • kubeconfig contexts/clusters/users created by minikube"
echo "  • Docker containers, networks, volumes, and images created by minikube"
echo
confirm "Proceed?" || { echo "Aborted."; exit 1; }

############################################
# 1) Delete all minikube profiles/clusters #
############################################
if command -v minikube >/dev/null 2>&1; then
  echo ">>> Listing minikube profiles..."
  # Robust list (falls back if JSON not available)
  PROFILES=$(minikube profile list -o json 2>/dev/null | \
    awk '/"Name":/ {gsub(/[",]/,""); print $2}' || true)

  if [[ -z "${PROFILES// }" ]]; then
    # Fallback: parse text output
    PROFILES=$(minikube profile list 2>/dev/null | awk 'NR>1 && $1 != "" {print $1}' | grep -v -E 'Profile|--------' || true)
  fi

  if [[ -n "${PROFILES// }" ]]; then
    echo "Found profiles:"
    echo "$PROFILES" | sed 's/^/  - /'
    for p in $PROFILES; do
      echo ">>> Deleting minikube profile: $p"
      run "minikube delete -p '$p' --all --purge"
    done
  else
    echo "No minikube profiles found."
  fi

  echo ">>> Global purge (just in case)…"
  run "minikube delete --all --purge || true"
fi

########################################
# 2) Remove ~/.minikube (cache, certs) #
########################################
if [[ -d "$HOME/.minikube" ]]; then
  echo ">>> Removing ~/.minikube directory…"
  run "rm -rf '$HOME/.minikube'"
else
  echo "~/.minikube already gone."
fi

#####################################################
# 3) Clean kubeconfig contexts/clusters/users (k8s) #
#####################################################
# We’ll remove entries likely created by minikube.
KUBECONFIG_FILE="${KUBECONFIG:-$HOME/.kube/config}"
if [[ -f "$KUBECONFIG_FILE" ]]; then
  echo ">>> Cleaning kubeconfig entries that look like minikube…"
  # Collect lists safely (ignore errors)
  CTXS=$(kubectl config get-contexts -o name 2>/dev/null || true)
  CLUS=$(kubectl config get-clusters 2>/dev/null | awk 'NR>1 {print $1}' || true)
  USERS=$(kubectl config get-users 2>/dev/null | awk 'NR>1 {print $1}' || true)

  for c in $CTXS; do
    if [[ "$c" =~ minikube|docker|newprofile ]]; then
      echo " - Deleting context: $c"
      run "kubectl config delete-context '$c' || true"
    fi
  done
  for cl in $CLUS; do
    if [[ "$cl" =~ minikube|docker|newprofile ]]; then
      echo " - Deleting cluster: $cl"
      run "kubectl config delete-cluster '$cl' || true"
    fi
  done
  for u in $USERS; do
    if [[ "$u" =~ minikube|docker|newprofile ]]; then
      echo " - Deleting user: $u"
      run "kubectl config unset users.'$u' || true"
    fi
  done
else
  echo "Kubeconfig not found at $KUBECONFIG_FILE; skipping."
fi

###############################################
# 4) Docker cleanup (containers/networks/etc) #
###############################################
if command -v docker >/dev/null 2>&1; then
  echo ">>> Cleaning Docker resources created by Minikube…"

  # Containers that are obviously Minikube
  CNT_IDS=$(docker ps -a --format '{{.ID}} {{.Names}} {{.Labels}}' 2>/dev/null | \
    awk '/minikube|kicbase|gcr\.io\/k8s-minikube/ {print $1}')
  if [[ -n "${CNT_IDS// }" ]]; then
    echo " - Removing containers:"
    echo "$CNT_IDS" | sed 's/^/   * /'
    run "docker rm -f $CNT_IDS"
  else
    echo " - No Minikube containers found."
  fi

  # Networks named minikube or created by minikube
  NET_IDS=$(docker network ls --format '{{.ID}} {{.Name}}' 2>/dev/null | \
    awk '/^.* (minikube|kind|kube.*)$/ {print $1}')
  # Minikube typically uses "minikube" network; include safety matches.
  if [[ -n "${NET_IDS// }" ]]; then
    echo " - Removing networks:"
    for n in $NET_IDS; do echo "   * $n"; done
    run "docker network rm $NET_IDS || true"
  else
    echo " - No Minikube networks found."
  fi

  # Volumes that look minikube-ish
  VOL_IDS=$(docker volume ls --format '{{.Name}}' 2>/dev/null | grep -E 'minikube|kube|kic' || true)
  if [[ -n "${VOL_IDS// }" ]]; then
    echo " - Removing volumes:"
    echo "$VOL_IDS" | sed 's/^/   * /'
    run "docker volume rm $VOL_IDS || true"
  else
    echo " - No Minikube volumes found."
  fi

  # Images: kicbase, preloads, gcr.io/k8s-minikube, bitnami test images pulled by minikube, etc.
  # We conservatively match common minikube artifacts.
  IMG_IDS=$(docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' 2>/dev/null | \
    awk '/kicbase|gcr\.io\/k8s-minikube|k8s-minikube|storage-provisioner/ {print $2}')
  if [[ -n "${IMG_IDS// }" ]]; then
    echo " - Removing images:"
    echo "$IMG_IDS" | sed 's/^/   * /'
    run "docker rmi -f $IMG_IDS || true"
  else
    echo " - No obvious Minikube images found."
  fi
fi

############################################
# 5) Optional: /etc/hosts stale entries    #
############################################
# Minikube seldom writes here, so we leave it alone. If you customized hosts,
# review it manually.

echo
echo "✅ Done."
if (( DRY_RUN )); then
  echo "(dry-run) Nothing was actually deleted."
fi