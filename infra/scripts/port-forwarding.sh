#!/usr/bin/env bash
set -euo pipefail

# --- config --------------------------------------------------------------
PAYMENT_NS=payment
MONITORING_NS=monitoring
LOGGING_NS=logging


# Set PF_INGRESS=true to force PF for ingress if you ever switch back from LoadBalancer
PF_INGRESS=${PF_INGRESS:-false}
ING_NS=ingress-nginx
INGRESS_LOCAL_PORT=${INGRESS_LOCAL_PORT:-8081}   # only used if PF_INGRESS=true

LOCKFILE="/tmp/k8s-port-forward.lock"

# --- helpers -------------------------------------------------------------
die() { echo "❌ $*" >&2; exit 1; }

lock() {
  if [[ -f "$LOCKFILE" ]]; then die "Port-forward script already running (rm $LOCKFILE to force)"; fi
  : > "$LOCKFILE"
}

remove_lock() { rm -f "$LOCKFILE" 2>/dev/null || true; }

wait_for_endpoints() {
  local ns="$1" svc="$2"
  echo "⏳ waiting for endpoints $ns/$svc ..."
  # wait until at least one address shows up
  until kubectl -n "$ns" get endpoints "$svc" \
    -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null | grep -q .; do
    sleep 2
  done
}

kill_local_port() {
  local port="$1"
  # macOS & Linux (lsof present by default on mac; often on linux)
  if command -v lsof >/dev/null 2>&1; then
    local pids
    pids=$(lsof -ti tcp:"$port" || true)
    if [[ -n "$pids" ]]; then
      echo "🔪 freeing localhost:$port (pids: $pids)"
      kill $pids 2>/dev/null || true
      sleep 0.2
      # if still alive, force kill
      pids=$(lsof -ti tcp:"$port" || true)
      [[ -n "$pids" ]] && kill -9 $pids 2>/dev/null || true
    fi
  else
    # fallback using ss (linux)
    if command -v ss >/dev/null 2>&1; then
      local pids
      pids=$(ss -lptn "sport = :$port" 2>/dev/null | awk -F'pid=' '/pid=/{sub(/\).*/,"",$2); print $2}')
      if [[ -n "$pids" ]]; then
        echo "🔪 freeing localhost:$port (pids: $pids)"
        kill $pids 2>/dev/null || true
        sleep 0.2
        pids=$(ss -lptn "sport = :$port" 2>/dev/null | awk -F'pid=' '/pid=/{sub(/\).*/,"",$2); print $2}')
        [[ -n "$pids" ]] && kill -9 $pids 2>/dev/null || true
      fi
    fi
  fi
}

pf_loop_svc() {
  local ns="$1" svc="$2" lport="$3" rport="$4"
  local backoff=2
  wait_for_endpoints "$ns" "$svc"
  while true; do
    echo "▶️  kubectl -n $ns port-forward svc/$svc $lport:$rport"
    # --address 127.0.0.1 limits to localhost; --request-timeout=0 disables client-side timeout
    if kubectl --request-timeout=0 --address=127.0.0.1 -n "$ns" port-forward "svc/$svc" "$lport:$rport"; then
      # exited normally (rare for PF), reset backoff and loop (will reconnect)
      backoff=2
    else
      echo "⚠️  svc/$svc port-forward dropped. retry in ${backoff}s…"
      sleep "$backoff"; backoff=$(( backoff < 60 ? backoff*2 : 60 ))
      # If the socket didn’t close cleanly, free it before retrying
      kill_local_port "$lport"
    fi
  done
}

cleanup() {
  echo "🛑 stopping all port-forwards…"
  # kill background kubectl PFs
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
  # ensure local ports are freed
  for port in "${LOCAL_PORTS[@]:-}"; do
    kill_local_port "$port"
  done
  remove_lock
  exit 0
}

# Ensure cleanup on most termination signals and on normal exit
trap cleanup INT TERM HUP QUIT
trap 'remove_lock' EXIT

# --- main ---------------------------------------------------------------
lock

declare -a PIDS=()
declare -a LOCAL_PORTS=()

# Define the forwards we want: ns kind/name  local  remote
FORWARDS=()

# Ingress (only if PF_INGRESS=true; otherwise you’re using minikube tunnel / LoadBalancer)
if [[ "$PF_INGRESS" == "true" ]]; then
  FORWARDS+=("$ING_NS svc/ingress-nginx-controller $INGRESS_LOCAL_PORT 80")
fi

# Keycloak
FORWARDS+=("$PAYMENT_NS svc/keycloak 8080 8080")

# Postgres
FORWARDS+=("$PAYMENT_NS svc/payment-db-postgresql 5432 5432")

#prometheus-operated 9090 9090 $MONITORING_NS & PIDS+=($!)
FORWARDS+=("$MONITORING_NS svc/prometheus-operated 9090 9090")


# Grafana
FORWARDS+=("$MONITORING_NS svc/prometheus-stack-grafana 3000 80")


#paymetn-service management
FORWARDS+=("$PAYMENT_NS svc/payment-service 9000 9000")

# Elasticsearch
FORWARDS+=("$LOGGING_NS svc/elasticsearch-master 9200 9200")

# Kibana
FORWARDS+=("$LOGGING_NS svc/kibana-kibana 5601 5601")


# Pre-free all local ports and start PF loops
for spec in "${FORWARDS[@]}"; do
  # shellcheck disable=SC2086
  set -- $spec
  ns="$1"; ref="$2"; lport="$3"; rport="$4"

  kill_local_port "$lport"
  LOCAL_PORTS+=("$lport")

  # Extract just the service name for pf_loop_svc
  svc="${ref#svc/}"

  pf_loop_svc "$ns" "$svc" "$lport" "$rport" & PIDS+=("$!")
done

echo "✅ Port-forwards running:"
[[ "$PF_INGRESS" == "true" ]] && echo "   - Ingress     → http://127.0.0.1:${INGRESS_LOCAL_PORT} (Host header still required)"
echo "   - Keycloak    → http://127.0.0.1:8080"
echo "   - Postgres    → 127.0.0.1:5432"
echo "   - Grafana     → http://127.0.0.1:3000"
echo "   - Promotheus     → http://127.0.0.1:9090"
echo "   - Elasticsearch → https://127.0.0.1:9200 (user: elastic, password: fUNIuW1Kdl3qFUyh%)"
echo "   - Kibana      → http://127.0.0.1:5601"
if [[ "$PF_INGRESS" != "true" ]]; then
  echo "   - Ingress     → via LoadBalancer (ensure 'minikube tunnel' is running)"
fi

echo "Press Ctrl+C to stop."
wait