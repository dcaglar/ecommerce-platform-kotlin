1. Service Health Metrics

🟢 Uptime / Liveness / Readiness
•	Spring Boot actuator /health endpoint (exported to Prometheus)
•	Container health checks (monitored in Prometheus/Grafana)

2. Outbox/Event Processing

🟦 Outbox Dispatcher
•	outbox_dispatcher_processed_total: Count of events dispatched (labeled by type: Payment, PaymentOrder)
•	outbox_dispatcher_failed_total: Count of failed dispatch attempts
•	outbox_dispatcher_lag_seconds: Time between event creation and dispatch (age)
•	outbox_dispatcher_scheduled_job_delay_seconds: Delay between scheduled job expected run and actual run

🟦 Outbox Table Backlog
•	outbox_new_events_gauge: Number of unsent (status=NEW) outbox events (by type)
•	High values mean stuck dispatcher/job, possible system issues

⸻

3. Kafka Consumer Metrics
   •	kafka_consumer_records_consumed_total (per topic/partition)
   •	kafka_consumer_lag (group, topic, partition) — critical for event-driven flows!
   •	kafka_consumer_errors_total: Deserialization/processing errors

⸻

4. Payment/Order Processing Metrics

🟩 Payment Flow Success/Failure
•	payments_created_total / payment_orders_created_total
•	payment_order_success_total: Successful PSP calls
•	payment_order_failed_total: Permanent failures (non-retryable)
•	payment_order_retry_requested_total: Retries triggered

🟨 Payment Order Processing Time
•	payment_order_processing_duration_seconds: Time from event emitted to result (successful or failed)
•	psp_response_time_seconds: Actual PSP call duration (p99, p95, avg)

⸻

5. Retry & DLQ Handling

🟧 Retry Pipeline
•	retries_scheduled_total: How many retries scheduled (by reason)
•	retry_queue_size: Gauge for Redis ZSet length
•	retry_success_total / retry_failed_total

🟥 DLQ
•	dlq_events_total: Events pushed to DLQ (by type/reason)
•	dlq_lag_seconds: Age of oldest event in DLQ

⸻

6. Resource & Infra Metrics

🟦 JVM/Process
•	jvm_memory_used_bytes
•	jvm_threads_live
•	system_cpu_usage
•	container_memory_usage_bytes / container_cpu_usage_seconds_total

🟩 DB Health
•	db_connection_active_count
•	db_query_duration_seconds

🟨 Redis/Kafka
•	redis_up / redis_connected_clients
•	kafka_broker_up / kafka_topic_partitions

⸻

7. Alerting (Initial Set)

🔴 Immediate Actions
•	Payment processing fails or DLQ threshold exceeded (DLQ > X, e.g., >10 in 5min)
•	Outbox dispatcher lag > 1 min (events not dispatched timely)
•	Payment order retry attempts exceed max retry policy (possible dead letter scenario)
•	Consumer lag > threshold (e.g., not keeping up)
•	Service down/unhealthy (readiness/liveness fail)
•	JVM memory/cpu usage >90%

⸻

8. Grafana Dashboards (MVP)
   •	Service health and uptime
   •	Event flow: Outbox ➝ Kafka ➝ Consumer ➝ PSP ➝ Result
   •	Payment/PaymentOrder stats
   •	Retries & DLQ overview
   •	Resource utilization




payment_service_outbox_dispatcher_processed_total{event_type="payment_order"}
payment_service_outbox_dispatcher_lag_seconds
payment_service_kafka_consumer_lag{topic="payment_order_created"}
payment_service_payment_order_success_total
payment_service_payment_order_failed_total
payment_service_retry_queue_size
payment_service_dlq_events_total