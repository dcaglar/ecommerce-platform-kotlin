# 1) Start a client pod (no restart policy)
```bash
kubectl run -n payment kafka-client \
  --restart=Never \
  --image=docker.io/bitnamilegacy/kafka:4.0.0-debian-12-r10 \
  --command -- sleep infinity
```

# 2) Shell into it
```bash
kubectl exec -it -n payment kafka-client -- bash
```

# 3) Use the tools (bootstrap is the in-cluster DNS your chart exposed)
```bash
kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 --list
kafka-consumer-groups.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 --list
```

```bash
kafka-consumer-groups.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --group payment-order-psp-call-executor-consumer-group --describe
```

```bash
kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --topic payment_order_psp_call_requested_topic --describe

kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --topic tpc --describe
```

```bash
kafka-transactions.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 list
```

# 3) Read from a topic by creating a separate consumer group, start from beginning
```bash
kafka-console-consumer.sh \
  --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --topic payment_order_psp_call_requested_topic.DLQ \
  --from-beginning \
  --group dlq-debug-psp-$(date +%s) \
  --property print.headers=true \
  --property print.timestamp=true
```

```bash
kafka-console-consumer.sh \
  --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --topic payment_order_psp_result_updated_topic.DLQ \
  --from-beginning \
  --group dlq-debug-psp-result-$(date +%s) \
  --property print.headers=true \
  --property print.timestamp=true \
  --max-messages 20
```

# 3) To see the total messages in a topic per partition
Command:
```bash
kafka-consumer-groups.sh \
  --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --group dlq-debug-psp-1758716393 \
  --describe
```

Example output:
```
Consumer group 'dlq-debug-psp-1758716393' has no active members.

GROUP                    TOPIC                                      PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG             CONSUMER-ID     HOST            CLIENT-ID

dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 7          60              103             43              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 38         38              133             95              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 9          27              105             78              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 40         32              75              43              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 11         31              98              67              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 42         32              125             93              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 13         28              63              35              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 44         27              99              72              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 15         20              72              52              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 46         16              22              6               -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 17         33              91              58              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 19         37              135             98              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 21         35              113             78              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 22         44              108             64              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 24         36              106             70              -               -               -
dlq-debug-psp-1758716393 payment_order_psp_call_requested_topic.DLQ 26         37              113             76              -               -               -
```

# Just watch new DLQ messages (don’t touch old ones)
```bash
kafka-console-consumer.sh \
  --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --topic payment_order_psp_call_requested_topic.DLQ \
  --group dlq-watch-$(date +%s) \
  --property print.headers=true \
  --property print.timestamp=true \
  --consumer-property auto.offset.reset=latest
```

```bash
kafka-console-consumer.sh \
  --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --topic payment_order_psp_result_updated_topic.DLQ \
  --group dlq-watch-$(date +%s) \
  --property print.headers=true \
  --property print.timestamp=true \
  --consumer-property auto.offset.reset=latest
```

```bash
kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --topic payment_order_created_topic --describe

kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --topic payment_order_psp_call_requested_topic --describe

kafka-topics.sh --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --topic payment_order_psp_result_updated_topic --describe
```

      payment_order_created_topic:            { partitions: 8, replicas: 1, createDlq: true }
      payment_order_psp_call_requested_topic: { partitions: 48, replicas: 1, createDlq: true }
      payment_status_check_scheduler_topic:   { partitions: 1, replicas: 1, createDlq: true }
      payment_order_succeeded_topic:          { partitions: 8, replicas: 1, createDlq: true }
      payment_order_psp_result_updated_topic:

```bash
/opt/bitnami/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka.payment.svc.cluster.local:9092 \
  --describe --topic __transaction_state
```
