helm upgrade --install yugabyte yugabytedb/yugabyte \
  --version 2.23.0 \
  -n "$NS" --create-namespace \
  -f "$VALUES_FILE"

# Removed: kubectl rollout status.
# YugabyteDB readiness is now guaranteed by the pg_isready initContainer
# in the yugabyte-db-init-job (Rule C). Dependent pods will not start
# until Yugabyte is confirmed ready a0t the manifest level.
kubectl -n "$NS" get pods,svc -l app.kubernetes.io/name=yugabyte -o wide