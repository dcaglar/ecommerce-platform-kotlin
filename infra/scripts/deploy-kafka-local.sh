#!/usr/bin/env bash
set -euo pipefail

NS="payment"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$REPO_ROOT"

VALUES_FILE="$REPO_ROOT/infra/helm-values/kafka-values-local.yaml"
RELEASE="kafka"
CHART="bitnami/kafka"
CHART_VERSION="32.3.14"

echo ">> Adding/updating Bitnami repo"
helm repo add bitnami https://charts.bitnami.com/bitnami >/dev/null 2>&1 || true
helm repo update >/dev/null

echo ">> Installing/upgrading $RELEASE in namespace $NS"
helm upgrade --install "$RELEASE" "$CHART" \
  --version "$CHART_VERSION" \
  -n "$NS" --create-namespace \
  -f "$VALUES_FILE" \
  --wait --timeout 10m

echo ">> Waiting for StatefulSet rollout"
kubectl -n "$NS" rollout status statefulset/kafka-controller --timeout=5m

echo ">> Kafka pods/services:"
kubectl -n "$NS" get pods,svc -l app.kubernetes.io/instance="$RELEASE" -o wide
echo ">> Extended smoke test"
kubectl -n "$NS" exec -it kafka-controller-0 -c kafka -- sh -lc '
  KAFKA_BIN=/opt/bitnami/kafka/bin
  echo "List topics:" && $KAFKA_BIN/kafka-topics.sh --bootstrap-server localhost:9092 --list

  TMP=__smoke_$$
  echo "Create $TMP" && $KAFKA_BIN/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic $TMP --partitions 1 --replication-factor 1
  echo "Describe $TMP" && $KAFKA_BIN/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic $TMP
  echo "Delete $TMP" && $KAFKA_BIN/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic $TMP
'