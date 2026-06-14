# 📊 Prometheus Monitoring Handbook

This handbook serves as the **mandatory reference** for monitoring the core system through Prometheus PromQL. You must actively track these specific metrics during all load tests, performance tuning sessions, and production troubleshooting. These are not optional.

## 1. Application Layer (Spring Boot / Micrometer)

### API Throughput (Requests / sec)
Measures the incoming request rate for the critical payment creation and authorization endpoints.
```promql
sum by (instance, method, uri) (
  rate(http_server_requests_seconds_count{
    uri=~"/api/v1/payments|/api/v1/payments/\\{paymentIntentId\\}/authorize", 
    method="POST"
  }[1m])
)
```

### API Latency (p95 and p99)
You must track the 95th and 99th percentile latencies (tail latency) for the critical endpoints to ensure SLAs are met. Do not rely on median (p50) metrics.

**p95 Latency:**
```promql
histogram_quantile(0.95, 
  sum by (le, uri) (
    rate(http_server_requests_seconds_bucket{
      uri=~"/api/v1/payments|/api/v1/payments/\\{paymentIntentId\\}/authorize", 
      method="POST"
    }[1m])
  )
)
```

**p99 Latency:**
```promql
histogram_quantile(0.99, 
  sum by (le, uri) (
    rate(http_server_requests_seconds_bucket{
      uri=~"/api/v1/payments|/api/v1/payments/\\{paymentIntentId\\}/authorize", 
      method="POST"
    }[1m])
  )
)
```

### Hikari Connection Pool (Bottleneck Tracking)
Tracks the maximum time threads spend waiting to acquire a connection from the database pool. If this spikes above a few milliseconds, the connection pool is starved.
```promql
max by (application, pool) (hikaricp_connections_acquire_seconds_max)
```
*To see active connections:*
```promql
sum by (application, pool) (hikaricp_connections)
```


## 2. Ingress Layer (Nginx)

### Total Ingress Requests (per minute)
Measures the absolute volume of traffic hitting the cluster's ingress controller for our namespace.
```promql
sum(increase(nginx_ingress_controller_requests{namespace="payment"}[1m]))
```

### Ingress Success Rate
Calculates the percentage of successful HTTP requests (ignoring 404s and client-aborted 499s).
```promql
sum(rate(nginx_ingress_controller_requests{status!~"[4-5].*", namespace="payment"}[1m])) 
/ 
(
  sum(rate(nginx_ingress_controller_requests{namespace="payment"}[1m])) 
  - 
  (sum(rate(nginx_ingress_controller_requests{status=~"404|499", namespace="payment"}[1m])) or vector(0))
)
```

## 3. Database Layer (PostgreSQL & Yugabyte)

We distinguish our databases based on the `datname` (database name) or `job` label.

### Transaction Commit Rate (TPS)
How many transactions are successfully committing per second.
```promql
# Edge DB (Local Cell Storage)
sum by (datname) (irate(pg_stat_database_xact_commit{datname=~"edge_.*"}[1m]))

# Central DB (Global Storage)
sum by (datname) (irate(pg_stat_database_xact_commit{datname=~"central_.*"}[1m]))
```

### Background Writer Buffers
Tracks how hard the database background writer is working to flush dirty pages to disk.
```promql
# Buffers written directly by backend processes (spikes indicate background writer is falling behind)
irate(pg_stat_bgwriter_buffers_backend_total[1m])

# Buffers flushed by backend via fsync
irate(pg_stat_bgwriter_buffers_backend_fsync_total[1m])

# Buffers cleaned by the background writer (normal behavior)
irate(pg_stat_bgwriter_buffers_clean_total[1m])
```

### YugabyteDB Specific Monitoring
Yugabyte natively exposes its own metrics via Prometheus.
```promql
# CPU Usage of Yugabyte nodes
avg by (instance) (rate(process_cpu_seconds_total{job=~".*yugabyte.*"}[1m]) * 1000)

# YSQL Query Latency (Microseconds)
histogram_quantile(0.95, sum by (le) (rate(rpc_latency_sum{job=~".*yugabyte.*"}[1m])))

# RocksDB SSTable Read/Write operations (Disk I/O pressure)
sum by (instance) (rate(rocksdb_number_keys_read[1m]))
sum by (instance) (rate(rocksdb_number_keys_written[1m]))
```

## 4. Kafka & Messaging

### Consumer Group Lag
Tracking the backlog of consumers is absolutely critical to the Separation of Powers architecture. If lag grows uncontrollably, the core database is not updating.
```promql
sum by (consumergroup, topic) (kafka_consumergroup_lag_sum)
```

### Dead Letter Queue (DLQ) Monitoring
You must monitor the DLQ topics to ensure messages are not systematically failing and being discarded by the consumers.

**Total Dead Letters Accumulated:**
```promql
sum by (topic) (kafka_topic_partition_current_offset{topic=~".*DLQ"})
```

**Rate of New Dead Letters (Failures/sec):**
Tracks if messages are actively failing right now. Any value greater than `0` means your application is actively throwing unrecoverable exceptions.
```promql
sum by (topic) (irate(kafka_topic_partition_current_offset{topic=~".*DLQ"}[1m]))
```

## 5. Kubernetes Cluster Resource Health

You must monitor the actual live resource utilization of the pods to ensure they are not hitting their limits and being CPU-throttled or OOMKilled.

### Live Pod CPU Usage (Cores)
Tracks the real-time CPU consumption across all pods in the `payment` namespace. If this approaches the requested limits, the pod will be throttled.
```promql
sum by (pod) (
  rate(container_cpu_usage_seconds_total{namespace="payment", container!=""}[1m])
)
```

### Live Pod Memory Usage (Bytes)
Tracks the working set memory. If a pod's working set exceeds its limit, Kubernetes will terminate it with an `OOMKilled` status.
```promql
sum by (pod) (
  container_memory_working_set_bytes{namespace="payment", container!=""}
)
```

### Node Resource Pressure
Immediately alerts if any physical node in the cluster is running out of fundamental resources. If this returns `1`, the cluster is completely saturated.
```promql
# Memory Pressure
sum by (node) (kube_node_status_condition{condition="MemoryPressure", status="true"})

# CPU Pressure / Disk Pressure
sum by (node) (kube_node_status_condition{condition="DiskPressure", status="true"})
```
