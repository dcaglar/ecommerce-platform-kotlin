Incremental ramp-up (recommended):
• First, run your test with 10 VUs for 10–15 minutes.
• Watch all your metrics (p95 latency, error rate, heap, Tomcat threads, HikariCP, etc).
• Make sure everything is stable (low latency, no errors, no resource exhaustion).
• Then, increase to 20 VUs for another 10–15 minutes.
• Watch again for any signs of degradation compared to the 10 VU run.

⸻

Why incrementally?
• You want to catch the first sign of performance bottleneck or instability at the lowest possible load.
• If you jump straight to 20 (or higher), you might skip seeing where the trouble starts.

⸻

TL;DR:
• Step 1: Test at 10 VUs → observe → document.
• Step 2: Test at 20 VUs → observe → document.
• Step 3: If still healthy, repeat at 50, 100, etc.

⸻

This approach is safer, gives you a clear performance profile, and makes root-cause analysis easier if/when something
breaks.

Let me know if you want a ready-to-use results log template!

Metric
Value
Threshold/Limit
Pass/Fail
Notes
p95 Latency (ms)
< 1000
p99 Latency (ms)
< 1500
Error Rate (%)
= 0
Tomcat Busy Threads
< 80% of max (e.g. <160/200)
Tomcat Max Threads
200
HikariCP Active Connections
< 80% of max
HikariCP Pending
0
JVM Heap Usage (%)
< 70% of max
JVM GC Pause (ms)
< 100
Kafka Consumer Lag
0
(if applicable)
System CPU (%)
< 80%
(optional)
System Load
< CPU core count
(optional)

Recommended Alerts for Each Stage

Set these in Grafana (alerting rules), or Prometheus Alertmanager if you use it.

⸻

Core alerts:

1. Latency SLO Breach
   • p95 latency (histogram_quantile(0.95, ...)) > 1s for 2m (adjust threshold for your SLO)
   • Panel: HTTP p95 latency

2. Error Rate
   • 5xx error rate (sum(rate(http_server_requests_seconds_count{status=~"5.."}[2m])) > 0)
   • Panel: HTTP 5xx Error Rate

3. Tomcat Busy Threads
   • tomcat_threads_busy_threads > 80% of tomcat_threads_config_max_threads for 2m (e.g., > 160 if max is 200)

4. HikariCP Pending Connections
   • hikaricp_connections_pending > 0 for 1m

5. JVM Heap Usage
   • Heap usage (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 80% for 2m

6. JVM GC Pause
   • GC pause time (rate(jvm_gc_pause_seconds_sum[2m])) > 0.1s (100ms) per scrape for 2m

7. Kafka Consumer Lag (if applicable)
   • Consumer lag (kafka_consumer_records_lag_max > 1000 or other suitable value) for 2m

⸻

Optional: System-level alerts
• CPU usage > 80%
• System load > # of cores
• Disk space < 10% free

⸻

How to phrase in Grafana:
• Pending period: 2m (to avoid flapping)
• Keep firing: 0s (or as needed)
• Severity: warning/critical (label as you wish)
• Evaluation group: (e.g., load-test-core, payment-capacity)

⸻

This way, you’ll know exactly when your service is close to—or breaching—its critical limits at every stage.

