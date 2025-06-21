Journey of Scalability, Reliability & Observability Milestones

1. Baseline: Naive Synchronous Flow
   • Everything was handled in a single HTTP request/response.
   • Lesson: Blocking on I/O (e.g., external PSP calls) made the system fragile and slow under load. Quick bottleneck:
   HTTP thread pool saturated.

⸻

2. Introduced Outbox Pattern & Asynchronous Processing
   • Moved PSP calls to Kafka-driven consumers.
   • HTTP endpoint now just enqueues the payment/order, responds immediately (202 Accepted).
   • Lesson: Decoupling request handling from downstream processing is critical for throughput and resilience.


3. Bottleneck #1: Downstream System/PSP Throughput
   • First real scalability limit: PSP calls (simulated or real) are much slower than Kafka queueing or DB.
   • Lesson: System throughput is always limited by the slowest downstream service.
   You observed that increasing HTTP capacity (threads/users) didn’t help if PSP remained slow.
   How did you solve thus bottleneck?

. Observable Symptoms
• Kafka consumer lag started to grow, even though your HTTP requests (via k6) were being accepted quickly and Tomcat
threads were not overloaded.
• HTTP requests returned 202/200 quickly, but “downstream processing” (payments actually being processed) was much
slower.
• CPU, DB, Tomcat thread pool, and Kafka itself were not saturated. No other obvious resource spike

Your Log Sequence (in order):

1. PSP cache lookup:
   TIMING: PSP cache lookup took 0 ms for 43040
   👉 Interpretation: No time wasted looking up cached results. Good.
2. PSP call (real, including cache):
   TIMING: Real PSP call took 2382 ms for paymentOrderId=43040
   TIMING: PSP call (including cache, if miss) took 2383 ms for 43040
   👉 Interpretation:
   • Your network call to PSP took ~2.3 seconds.
   • This is the main contributor to total latency for this payment.
3. PSP returned:
   ✅ PSP returned SUCCESSFUL for 43040
   👉 Interpretation: Call succeeded.
4. processPspResult timings:
   • TIMING: processPspResult (DB/write) took 5 ms for 43040
   • TIMING: processPspResult (Total) took 4 ms for 43040
   👉 Interpretation:
   • Your actual logic for handling and persisting the result is very fast (just 4–5 ms for DB write and total logic).
5. Handler total time:
   TIMING: Total handler time: 2388 ms for 43040
   👉 Interpretation:
   • Your end-to-end handler (from message received until fully processed) is almost entirely dominated by the PSP call
   time.

⸻

What’s the Bottleneck?

Not your DB. Not your app logic. Not your threading.
• PSP call (external dependency) is your bottleneck.
• DB writes, cache lookups, and processing are nearly instant.

⸻

Visual Interpretation (from your logs):
![](/Users/dogancaglar/Desktop/Screenshot 2025-06-15 at 13.55.13.png)

Conclusion:

The slow part is outside your service, in the PSP (simulated) response time.
If your PSP gets faster, your whole system gets faster.

Step-by-Step Guide to Diagnose Resource Efficiency

1. Simulate a Fast PSP
   • Make the PSP return instantly (e.g., sleep = 0ms or just return immediately).
   • This way, your application logic and DB become the main factors.
   • The “Total handler time” should drop dramatically (probably just a few ms per payment order).


2. Increase Load
   • Run a heavy load test (e.g., K6, Locust, JMeter, or your custom tool) with hundreds or thousands of concurrent
   requests.
   • Goal: See how many requests per second (RPS) your app can handle when it’s not blocked by external calls.

   ACTION
   • A. Load Test & Metrics
   • You ran a K6 load test with 600 VUs for 10 minutes.
   • Grafana/Prometheus dashboards showed:
   • DB Pool Utilization spiking
   • HTTP Request Rate dropping
   • Increased HTTP latency (p99, p95)
   • Outbox event backlog growing.
   • Kibana logs and infrastructure logs didn’t show app-level errors, confirming the bottleneck was not infra-related (
   CPU, memory, etc).
   • B. Database Query Analysis
   • You enabled pg_stat_statements to track slowest queries.
   • Ran a query on pg_stat_statements to list top time-consuming queries.
   • Found your biggest time sinks were:
   • SELECT ... FROM outbox_event WHERE id=?
   • SELECT ... FROM outbox_event WHERE status=? ORDER BY created_at LIMIT ?
   • INSERT INTO outbox_event ...
   • SELECT count(id) FROM outbox_event WHERE status=?
   • C. Application Code Mapping
   • You mapped these queries to specific repository methods:
   • Example: findByStatusOrderByCreatedAtAsc(status: String, pageable: Pageable)
   (Maps to: SELECT ... WHERE status=? ORDER BY created_at LIMIT ?)
   • Noted that this was being called very frequently by your outbox dispatcher scheduler.

Step-by-Step: Database Improvements and Configuration Changes

1. Enabling pg_stat_statements

Goal:
Get visibility into which queries are slow or called most often.

Actions:
• Tried CREATE EXTENSION pg_stat_statements;
→ Got error: “must be loaded via shared_preload_libraries”
• Changed DB configuration:
• Edited postgresql.conf
• Added/modified:
SELECT
query,
calls,
total_exec_time AS total_time,
mean_exec_time AS mean_time,
rows,
(total_exec_time / calls) AS avg_time_per_call
FROM
pg_stat_statements
ORDER BY
total_exec_time DESC
LIMIT 10;

How You Identified the Problem (Your Process):

1. Monitor: Noticed high DB times and slow endpoints via Prometheus/Grafana.
2. Investigate: Used pg_stat_statements to find slowest/top total time queries.
3. Analyze: Ran EXPLAIN ANALYZE on those queries.
4. Fix: Found missing index → created it.
5. Verify: Saw improved metrics and faster response time.

no pk on payment table and no index on status column of outbox table.

4. Watch Your Metrics
   • CPU usage: Is it spiking? Are you fully utilizing your CPUs or sitting idle?
   • Thread pools: Are threads waiting or maxed out? (Spring Boot actuator endpoints and JVM tools like VisualVM can
   show this.)
   • Database:
   • Look at DB CPU usage, connection pool stats, and query latencies.
   • If your DB is the bottleneck, you’ll see handler times increase as you push more load.
   • Queue sizes (if you use async queues): Are any internal queues (Kafka, Redis, etc.) backing up?

4. Monitor Handler Time Distribution
   • With PSP “out of the way,” any increase in total handler time is now due to your code or your DB.
   • Use your existing Kibana visualizations for average/max/percentile handler times.

5. Watch for Saturation or Errors
   • Look for signs of saturation:
   • Growing HTTP response times
   • Increased error rates (timeouts, 5xx)
   • Thread pool exhaustion (can’t accept new tasks)
   • If you see this before hardware is maxed, you may have hidden bottlenecks (e.g., locking, connection pool limits,
   non-optimal queries).

⸻

What Will This Show?
• If your app is efficient:
• You should be able to process hundreds/thousands of payment orders per second per core/thread, limited by your
database or hardware.
• CPU should be busy but not pegged at 100%.
• No weird spikes in handler times or thread pool rejections.
• If you have an inefficiency:
• You’ll see handler times increase as load increases, even though PSP is fast.
• You might see high CPU usage with little throughput (bad code, contention, blocking).
• Thread pool or DB connection pool maxed out.
• Garbage collection or locking spikes (rare, but possible)..

        Metric Correlation

• You monitored:
• PSP call latency metric (via Micrometer timer in safePspCall)
• Consumer processing time
• Both metrics showed that each payment event spent most of its consumer time waiting for the PSP simulation (often
100ms–2000ms+ depending on the scenario).

      Scaling Experiment

• You tried increasing HTTP concurrency (more k6 users, more Tomcat threads).
• Result: No improvement in the downstream processing speed—consumer lag continued to increase if load > PSP capacity.
• You tried scaling Kafka consumers (more consumer threads/partitions).
• Result: No improvement if PSP calls remained slow; improvement only if you had more partitions AND the PSP was not
saturated

    Direct Observation

• PSP scenario simulation: When you changed the psp.simulation.currentScenario to slower/faster settings, the downstream
processing directly sped up or slowed down.
• Logs: Your consumer logs showed that safePspCall was always the slowest part (calls were taking a long time and often
timing out).

5. Process of Elimination
   • DB, HTTP server, Kafka, and network all showed low or moderate usage.
   • Only the PSP call duration was consistently high.
   ⸻

4. Introduced Observability: Prometheus & Grafana
   • Tracked key metrics:
   • HTTP request/sec, latency distributions
   • Kafka consumer lag
   • PSP call duration
   • Tomcat thread pool utilization
   • Database connection pool saturation
   • Lesson: You can’t optimize what you don’t measure. The right metrics make bottlenecks obvious.

⸻

5. Bottleneck #2: Tomcat Thread Pool
   • Observed: Under load, Tomcat max threads were exhausted (200 busy of 200 max).
   • Lesson: Even with async, you need enough HTTP threads to handle incoming traffic—but just increasing them only
   helps until the next bottleneck.

⸻

6. Monitoring Kafka: Lag and Throughput
   • Tracked:
   • kafka_consumer_fetch_manager_records_lag_max
   • Observed consumer lag growing when downstream was slow.
   • Lesson: Kafka lag is the “canary” of your pipeline. If lag spikes, your consumers can’t keep up.

⸻

7. Scaling Out Consumers
   • Lesson: For partitioned Kafka topics, you can scale horizontally (add more consumers/pods) to process more in
   parallel—up to the number of partitions.

⸻

8. Partitioning Kafka Topics by aggregateId
   • Introduced: Partitioning by aggregateId (e.g., paymentOrderId) to guarantee in-order processing for a single
   aggregate.
   • Lesson: Partitioning strategy is fundamental for both scalability and correctness.
   • Partitioning enables parallelism and enforces order for the same key.

⸻

9. Reliability: Defensive Event Processing
   • Lesson:
   • Don’t trust producers.
   • Always check, “does the referenced aggregate exist?” before processing events.
   • With partitioning, you can also buffer/retry events that arrive out of order.
   • Without partitioning, this is nearly impossible.

⸻

10. Performance Monitoring & Alerts
    • Set up:
    • Alerting on high HTTP/PSP latency, thread pool saturation, Kafka lag, and DB pool usage.
    • Lesson: Early warnings (alerts) help you react before the system falls over.

⸻

11. Scaling Limits & Systemic Trade-offs
    • Lesson:
    • You can only scale each component up to its own bottleneck.
    • You must know which part (web server, consumer, PSP, DB, Kafka) is the current bottleneck, and design accordingly.
    • Elastic scaling (horizontal) is easier with stateless, partitioned, async components.

⸻

12. Reliability Features
    • Implemented:
    • Idempotency (to prevent double-processing)
    • Retry & DLQ (for transient and permanent failures)
    • Observability (logging with trace IDs)
    • Lesson: High throughput systems must be both scalable and resilient—able to recover from, and surface, errors.

⸻

Summary of Key Takeaways
• Never trust the producer: Always defensively check data.
• Measure everything: Bottlenecks are only obvious with the right metrics and dashboards.
• Partitioning is not just for scaling: It guarantees order and enables reliable event-driven orchestration.
• Async architecture improves both throughput and resilience—but only if you monitor, alert, and tune every hop.
• You hit limits one by one: PSP, HTTP, thread pools, consumers, DB pool… so you have to continuously tune and
re-architect for the next weakest link.

⸻

Let me know if you want this formatted differently (e.g., table, bullet-only), or want any code/diagram illustrations
for your eventual write-up or presentation!