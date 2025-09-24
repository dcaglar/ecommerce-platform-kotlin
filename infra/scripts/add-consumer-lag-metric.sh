#!/usr/bin/env bash
set -euo pipefail

# --- Config (override via env or args) ----------------------------------------
RELEASE="${1:-payment-consumers}"                     # Helm release name
CHART="${2:-./charts/payment-consumers}"              # Chart path or repo/chart
NAMESPACE="${NAMESPACE:-payment}"                     # k8s namespace
VALUES_FILES=()                                       # e.g. ("-f" "values.yaml" "-f" "values.local.yaml")

APP_LABEL="${APP_LABEL:-$(echo "$RELEASE" | tr '[:upper:]' '[:lower:]')}" # usually matches app label
DEPLOY_NAME="${DEPLOY_NAME:-$RELEASE}"

# --- Deploy -------------------------------------------------------------------
echo "▶️  Helm upgrade/install: $RELEASE in ns=$NAMESPACE using chart=$CHART"
helm upgrade --install "$RELEASE" "$CHART" -n "$NAMESPACE" "${VALUES_FILES[@]}"

echo
echo "⏳ Waiting for rollout to complete..."
echo "  $ kubectl rollout status deploy/$DEPLOY_NAME -n $NAMESPACE"
kubectl rollout status "deploy/$DEPLOY_NAME" -n "$NAMESPACE"

# --- Helper: print + run ------------------------------------------------------
run() { echo -e "\n$ $*"; eval "$@"; }

# --- Core: Pods/Describe/Events ----------------------------------------------
echo -e "\n\n🔎  BASIC INSPECTION COMMANDS"
run kubectl get deploy "$DEPLOY_NAME" -n "$NAMESPACE" -o wide
run kubectl get rs -n "$NAMESPACE" | grep "$DEPLOY_NAME" || true
run kubectl get pods -n "$NAMESPACE" -l "app=$APP_LABEL" -o wide
run kubectl describe deploy "$DEPLOY_NAME" -n "$NAMESPACE" | sed -n '1,180p'
run kubectl get events -n "$NAMESPACE" --sort-by=.lastTimestamp | tail -n 30

# --- Logs: initContainer + main container ------------------------------------
echo -e "\n\n🧰  LOGS (initContainer + main container)"
# one-liners you can copy during live debugging:
echo "$ kubectl logs deploy/$DEPLOY_NAME -n $NAMESPACE -c create-consumer-user --since=10m"
echo "$ kubectl logs deploy/$DEPLOY_NAME -n $NAMESPACE -c payment-consumers --since=10m"
# and now show a small tail to confirm the init SQL ran
POD="$(kubectl get pods -n "$NAMESPACE" -l "app=$APP_LABEL" -o jsonpath='{.items[0].metadata.name}')"
if [[ -n "${POD:-}" ]]; then
  run kubectl logs "$POD" -n "$NAMESPACE" -c create-consumer-user --tail=80 || true
  run kubectl logs "$POD" -n "$NAMESPACE" -c payment-consumers --tail=50 || true
fi

# --- Health endpoints (if Actuator exposed) -----------------------------------
echo -e "\n\n❤️  LIVENESS/READINESS (if Spring Actuator enabled on :8080)"
echo "$ kubectl port-forward deploy/$DEPLOY_NAME 8080:8080 -n $NAMESPACE"
echo "  Then visit:"
echo "    http://localhost:8080/actuator/health/liveness"
echo "    http://localhost:8080/actuator/health/readiness"

# --- HPA + metrics (requires metrics-server) ----------------------------------
echo -e "\n\n📈  HPA & METRICS"
run kubectl get hpa -n "$NAMESPACE" | (grep "$DEPLOY_NAME" || true)
echo "$ kubectl describe hpa/$DEPLOY_NAME -n $NAMESPACE"
echo "$ kubectl top pods -n $NAMESPACE -l app=$APP_LABEL"
echo "$ kubectl top nodes"

# --- Config/Secrets quick checks ----------------------------------------------
echo -e "\n\n🔐  CONFIGMAPS & SECRETS (existence and key names)"
run kubectl get configmap payment-app-config -n "$NAMESPACE" -o yaml | sed -n '1,120p' || true
run kubectl get secret payment-db-credentials -n "$NAMESPACE" -o jsonpath='{.data}' | jq -r 'keys[]' || true

echo -e "\n📤  Decode specific secret keys (change keys as needed):"
echo "$ kubectl get secret payment-db-credentials -n $NAMESPACE -o jsonpath='{.data.PAYMENT_CONSUMERS_APP_DB_USER}' | base64 -d; echo"
echo "$ kubectl get secret payment-db-credentials -n $NAMESPACE -o jsonpath='{.data.PAYMENT_CONSUMERS_APP_DB_PASSWORD}' | base64 -d; echo"

# --- Postgres quick login test (from a throwaway psql pod) --------------------
echo -e "\n\n🐘  POSTGRES QUICK TEST (run if you want to verify consumer creds)"
echo "# Launch a temp psql:"
echo "$ kubectl run psql-tmp -n $NAMESPACE --rm -it --image=postgres:16-alpine --restart=Never -- bash"
echo "# Inside the pod, run:"
echo "  export DB_NAME=\$(kubectl get cm payment-app-config -n $NAMESPACE -o jsonpath='{.data.DB_NAME}')"
echo "  export PGUSER=\$(kubectl get secret payment-db-credentials -n $NAMESPACE -o jsonpath='{.data.PAYMENT_CONSUMERS_APP_DB_USER}' | base64 -d)"
echo "  export PGPASSWORD=\$(kubectl get secret payment-db-credentials -n $NAMESPACE -o jsonpath='{.data.PAYMENT_CONSUMERS_APP_DB_PASSWORD}' | base64 -d)"
echo "  psql -h payment-db-postgresql -U \"\$PGUSER\" -d \"\$DB_NAME\" -c 'select current_user, now()'"

# --- Kafka DLQ quick commands (if you have a kafka-client pod) ----------------
echo -e "\n\n🪵  KAFKA DLQ QUICK CHECKS (assuming a pod named/labelled kafka-client)"
echo "# Get a kafka-client pod name:"
echo "$ KPod=\$(kubectl get pods -n $NAMESPACE -l app=kafka-client -o jsonpath='{.items[0].metadata.name}')"
echo "# Tail only NEW DLQs:"
echo "$ kubectl exec -n $NAMESPACE \"\$KPod\" -- bash -lc 'kafka-console-consumer.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 --topic payment_order_psp_call_requested_topic.DLQ --group dlq-watch-\$(date +%s) --property print.headers=true --property print.timestamp=true --consumer-property auto.offset.reset=latest'"
echo "# Count backlog via a fresh group:"
echo "$ kubectl exec -n $NAMESPACE \"\$KPod\" -- bash -lc 'kafka-consumer-groups.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 --group dlq-count-\$(date +%s) --describe | awk '\''\$1 ~ /\\.DLQ\$/ {sum+=\$NF} END{print sum+0}'\'' '"

# --- Events on the specific pod (handy for CrashLoop) -------------------------
echo -e "\n\n🚨  POD EVENTS (useful for CrashLoop, ImagePull, OOM, Probe failures)"
if [[ -n "${POD:-}" ]]; then
  run kubectl describe pod "$POD" -n "$NAMESPACE" | sed -n '/Events:/,$p'
else
  echo "$ kubectl describe pod <pod-name> -n $NAMESPACE | sed -n '/Events:/,\$p'"
fi

echo -e "\n✅ Done. Above you have ready-to-copy commands for: rollout, pods, logs (init/app), HPA, metrics, events, secrets, DB login, and Kafka DLQ."