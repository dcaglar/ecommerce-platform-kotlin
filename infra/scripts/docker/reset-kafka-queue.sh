#!/bin/bash
set -e

# ---- CONFIG ----
BOOTSTRAP=localhost:29092

# List your topics (add any others as needed)
TOPICS=(
  payment_order_created_topic
  payment_order_created_topic.DLQ
  payment_order_retry_request_topic
  payment_order_retry_request_topic.DLQ
  payment_status_check_scheduler_topic
  payment_status_check_scheduler_topic.DLQ
  payment_order_succeeded_topic
  payment_order_succeeded_topic.DLQ
)

# 1. Delete topics
for topic in "${TOPICS[@]}"; do
  echo "Deleting topic: $topic"
  kafka-topics --bootstrap-server "$BOOTSTRAP" --delete --topic "$topic" || true
done

# 2. Find and delete all relevant consumer groups (safest: delete ALL groups)
echo "Listing all consumer groups..."
ALL_GROUPS=$(kafka-consumer-groups --bootstrap-server "$BOOTSTRAP" --list)
for group in $ALL_GROUPS; do
  echo "Deleting group: $group"
  kafka-consumer-groups --bootstrap-server "$BOOTSTRAP" --delete --group "$group" || true
done

# 3. (No direct way to clear __consumer_offsets. Deleting topics and groups makes them irrelevant.)
echo "Reset complete. All topics and consumer groups removed."

## 4. Optionally, recreate your topics (using your Spring config or manually)
#echo "If you want, recreate topics with:"
#for topic in "${TOPICS[@]}"; do
#  echo "kafka-topics.sh --bootstrap-server \"$BOOTSTRAP\" --create --topic \"$topic\" --partitions 1 --replication-factor 1"

#done