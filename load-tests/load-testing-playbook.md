# 🚀 Performance Engineering & Load Testing Playbook

This playbook defines the exact methodology for discovering your system's limits, diagnosing bottlenecks, and scientifically validating architectural changes (even when you have zero historical production traffic).

> **Core Philosophy**
> When you lack production data, you must invert your strategy: **Don't guess your expected traffic. Instead, scientifically discover the absolute maximum capacity of your current hardware.** Use that ceiling to set your baseline expectations.

---

## 📊 RPS-Driven Load Test Profiles
Our `k6-payment-flow.js` uses **RPS-driven (Arrival-Rate) Executors**. Because our flow contains **2 API calls** (Create + Authorize), the *Total Server Load* hitting the API is always **Double (2x)** the Target Rate.

1. **`single` & `smoke` (The Baseline Tests)**
   - **Target Rate:** 1 Flow / sec  ➔  **Total Load: 2 API Requests / sec**
   - **Purpose:** Quick sanity checks to verify API health.
2. **`average` (Daily Traffic Simulation)**
   - **Target Rate:** 50 Flows / sec  ➔  **Total Load: 100 API Requests / sec**
   - **Purpose:** Simulates standard day-to-day traffic. Ramps up, holds steady, then cools down.
3. **`soak` (The Endurance Test)**
   - **Target Rate:** 30 Flows / sec  ➔  **Total Load: 60 API Requests / sec**
   - **Purpose:** Instantly turns on a steady stream for 1 full hour to uncover slow memory/connection leaks.
4. **`stress` (The Heavy Load)**
   - **Target Rate:** 100 Flows / sec  ➔  **Total Load: 200 API Requests / sec**
   - **Purpose:** Pushes the system past daily averages to see how connection pools and queues handle pressure.
5. **`spike` (The Flash Sale)**
   - **Target Rate:** 400 Flows / sec  ➔  **Total Load: 800 API Requests / sec**
   - **Purpose:** Replicates a Black Friday burst to test Kafka buffering and AutoScaling under extreme shock.
6. **`breakpoint` (The Death Test)**
   - **Target Rate:** Ramps to 1,000 Flows / sec  ➔  **Total Load: 2,000 API Requests / sec**
   - **Purpose:** Slowly and relentlessly adds traffic over 15 minutes to find the exact mathematical breaking point.

---

## 🛑 Phase 1: The Sanity Check (Smoke Test)
Before pushing the system, you must prove that the infrastructure is correctly wired and capable of serving the absolute "best-case scenario" with zero resource contention.

1. **Run the Command:** 
   ```bash
   k6 run -e PROFILE=smoke load-tests/k6-payment-flow.js
   ```
2. **What to Verify:**
   - Error rate (`http_req_failed`) is exactly `0%`.
   - Record the `ttfb_backend_processing` latency.
3. **The Outcome:** 
   This is your **Minimal Load Baseline**. If this takes seconds instead of milliseconds, stop immediately—your core code, database indexes, or network routing is fundamentally broken.

---

## 🧗 Phase 2: Finding the Ceiling (Breakpoint Test)
This phase discovers exactly what your
{"@timestamp":"2026-06-22T19:09:25.118+02:00","level":"INFO","thread_name":"payment-service-spring-scheduled-2","logger_name":"com.zaxxer.hikari.HikariDataSource","message":"central-edge-worker-pool - Start completed."}
{"@timestamp":"2026-06-22T19:09:27.627+02:00","level":"ERROR","thread_name":"payment-service-spring-scheduled-1","logger_name":"org.springframework.scheduling.support.TaskUtils$LoggingErrorHandler","message":"Unexpected error occurred in scheduled task","stack_trace":"org.springframework.dao.DataAccessResourceFailureException: com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.LocalOutboxMapperForEdgeWorker.reclaimStuckClaims (batch index #1) failed. Cause: java.sql.BatchUpdateException: Batch entry 0 UPDATE outbox_event\n        SET status = 'NEW',\n        claimed_at = NULL,\n        claimed_by = NULL\n        WHERE status = 'PROCESSING'\n        AND claimed_at < ((now() AT TIME ZONE 'UTC') - (('600'::int4) || ' seconds')::interval) was aborted: ERROR: canceling statement due to statement timeout  Call getNextException to see other errors in the batch.\n; Batch entry 0 UPDATE outbox_event\n        SET status = 'NEW',\n        claimed_at = NULL,\n        claimed_by = NULL\n        WHERE status = 'PROCESSING'\n        AND claimed_at < ((now() AT TIME ZONE 'UTC') - (('600'::int4) || ' seconds')::interval) was aborted: ERROR: canceling statement due to statement timeout  Call getNextException to see other errors in the batch.\n\tat org.springframework.jdbc.support.SQLStateSQLExceptionTranslator.doTranslate(SQLStateSQLExceptionTranslator.java:121)\n\tat org.springframework.jdbc.support.AbstractFallbackSQLExceptionTranslator.translate(AbstractFallbackSQLExceptionTranslator.java:107)\n\tat org.springframework.jdbc.support.AbstractFallbackSQLExceptionTranslator.translate(AbstractFallbackSQLExceptionTranslator.java:116)\n\tat org.springframework.jdbc.support.AbstractFallbackSQLExceptionTranslator.translate(AbstractFallbackSQLExceptionTranslator.java:116)\n\tat org.mybatis.spring.MyBatisExceptionTranslator.translateExceptionIfPossible(MyBatisExceptionTranslator.java:92)\n\tat org.mybatis.spring.SqlSessionUtils$SqlSessionSynchronization.beforeCommit(SqlSessionUtils.java:293)\n\tat org.springframework.transaction.support.TransactionSynchronizationUtils.triggerBeforeCommit(TransactionSynchronizationUtils.java:127)\n\tat org.springframework.transaction.support.AbstractPlatformTransactionManager.triggerBeforeCommit(AbstractPlatformTransactionManager.java:986)\n\tat org.springframework.transaction.support.AbstractPlatformTransactionManager.processCommit(AbstractPlatformTransactionManager.java:775)\n\tat org.springframework.transaction.support.AbstractPlatformTransactionManager.commit(AbstractPlatformTransactionManager.java:758)\n\tat org.springframework.transaction.interceptor.TransactionAspectSupport.commitTransactionAfterReturning(TransactionAspectSupport.java:698)\n\tat org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:416)\n\tat org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)\n\tat org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)\n\tat org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)\n\tat com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler.LocalOutboxStoreAndForwardJob$$SpringCGLIB$$0.reclaimStuck(<generated>)\n\tat java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)\n\tat java.base/java.lang.reflect.Method.invoke(Unknown Source)\n\tat org.springframework.scheduling.support.ScheduledMethodRunnable.runInternal(ScheduledMethodRunnable.java:130)\n\tat org.springframework.scheduling.support.ScheduledMethodRunnable.lambda$run$2(ScheduledMethodRunnable.java:124)\n\tat io.micrometer.observation.Observation.observe(Observation.java:498)\n\tat org.springframework.scheduling.support.ScheduledMethodRunnable.run(ScheduledMethodRunnable.java:124)\n\tat org.springframework.scheduling.config.Task$OutcomeTrackingRunnable.run(Task.java:85)\n\tat org.springframework.scheduling.support.DelegatingErrorHandlingRunnable.run(DelegatingErrorHandlingRunnable.java:54)\n\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Unknown Source)\n\tat java.base/java.util.concurrent.FutureTask.runAndReset(Unknown Source)\n\tat java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(Unknown Source)\n\tat com.dogancaglar.paymentservice.config.MdcTaskDecorator.decorate$lambda$0(ThreadPoolConfig.kt:98)\n\tat org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler$DelegatingRunnableScheduledFuture.run(ThreadPoolTaskScheduler.java:508)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)\n\tat java.base/java.lang.Thread.run(Unknown Source)\nCaused by: java.sql.BatchUpdateException: Batch entry 0 UPDATE outbox_event\n        SET status = 'NEW',\n        claimed_at = NULL,\n        claimed_by = NULL\n        WHERE status = 'PROCESSING'\n        AND claimed_at < ((now() AT TIME ZONE 'UTC') - (('600'::int4) || ' seconds')::interval) was aborted: ERROR: canceling statement due to statement timeout  Call getNextException to see other errors in the batch.\n\tat org.postgresql.jdbc.BatchResultHandler.handleError(BatchResultHandler.java:165)\n\tat org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2413)\n\tat org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:579)\n\tat org.postgresql.jdbc.PgStatement.internalExecuteBatch(PgStatement.java:912)\n\tat org.postgresql.jdbc.PgStatement.executeBatch(PgStatement.java:936)\n\tat org.postgresql.jdbc.PgPreparedStatement.executeBatch(PgPreparedStatement.java:1733)\n\tat com.zaxxer.hikari.pool.ProxyStatement.executeBatch(ProxyStatement.java:128)\n\tat com.zaxxer.hikari.pool.HikariProxyPreparedStatement.executeBatch(HikariProxyPreparedStatement.java)\n\tat org.apache.ibatis.executor.BatchExecutor.doFlushStatements(BatchExecutor.java:126)\n\tat org.apache.ibatis.executor.BaseExecutor.flushStatements(BaseExecutor.java:129)\n\tat org.apache.ibatis.executor.BaseExecutor.flushStatements(BaseExecutor.java:122)\n\tat org.apache.ibatis.executor.BaseExecutor.commit(BaseExecutor.java:248)\n\tat org.apache.ibatis.executor.CachingExecutor.commit(CachingExecutor.java:120)\n\tat org.apache.ibatis.session.defaults.DefaultSqlSession.commit(DefaultSqlSession.java:223)\n\tat org.apache.ibatis.session.defaults.DefaultSqlSession.commit(DefaultSqlSession.java:217)\n\tat org.mybatis.spring.SqlSessionUtils$SqlSessionSynchronization.beforeCommit(SqlSessionUtils.java:289)\n\t... 26 common frames omitted\nCaused by: org.postgresql.util.PSQLException: ERROR: canceling statement due to statement timeout\n\tat org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2725)\n\tat org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2412)\n\t... 40 common frames omitted\n"}
{"@timestamp":"2026-06-22T19:09:28.065+02:00","level":"WARN","thread_name":"outbox-dispatcher-pool-3","logger_name":"com.zaxxer.hikari.pool.PoolBase","message":"central-edge-worker-pool - Failed to validate connection org.postgresql.jdbc.PgConnection@ccd9e4d (This connection has been closed.). Possibly consider using a shorter maxLifetime value."}
{"@timestamp":"2026-06-22T19:09:28.066+02:00","level":"WARN","thread_name":"outbox-dispatcher-pool-3","logger_name":"com.zaxxer.hikari.pool.PoolBase","message":"central-edge-worker-pool - Failed to validate connection org.postgresql.jdbc.PgConnection@43376b9f (This connection has been closed.). Possibly consider using a shorter maxLifetime value."}
{"@timestamp":"2026-06-22T19:09:28.067+02:00","level":"WARN","thread_name":"outbox-dispatcher-pool-3","logger_name":"com.zaxxer.hikari.poolcurrent hardware configuration can handle before it collapses.

1. **Run the Command:** 
   ```bash
   k6 run -e PROFILE=breakpoint load-tests/k6-payment-flow.js
   ```
   *(This slowly ramps up from 0 to 1000 users over 15 minutes).*
2. **Watch the Symptoms (Grafana):** 
   Open your Payment Dashboard and keep your eyes glued to two panels:
   - `HTTP Requests Per Second (/payments)`
   - `HTTP Latency Percentiles (p50/p95/p99)`
3. **Identify the "Knee in the Curve":** 
   Wait for the exact moment where the RPS line flatlines (stops growing), and the p95 Latency suddenly skyrockets. 
4. **The Outcome:** 
   Note the exact number of API Requests Per Second (RPS) hitting the server at that moment (e.g., `120 RPS`). This is your **Maximum Efficient Capacity**.

---

## 🕵️ Phase 3: The Bottleneck Diagnosis
The moment the system breaks in Phase 2, freeze the test and scan your Grafana Dashboard to find out *why* it broke. Check these suspects in order:

> **WARNING**: Only **one** of these will usually hit 100% first. That is your primary bottleneck.

1. **The Database (Hikari Starvation)**
   - **Check:** `DB Connection Pool Utilization (%)`
   - **If 100%:** Increase Hikari pool size, optimize slow SQL queries, or scale database hardware.
2. **The Web Server (Thread Starvation)**
   - **Check:** `Tomcat Thread Pool Utilizatioin %`
   - **If 100%:** Increase Tomcat max-threads or scale up the number of `payment-service` Pods.
3. **The Compute (CPU/Memory Thrashing)**
   - **Check:** `CPU Usage (%)` & `GC Pause (max ms)`
   - **If 100% or GC > 500ms:** The JVM is thrashing. You need more Pods (Horizontal Scaling) or higher RAM limits (Vertical Scaling).
4. **The Asynchronous Backbone (Message Queueing)**
   - **Check:** `Kafka Consumer Lag by Topic` & `Outbox Event Backlog`
   - **If climbing infinitely:** Your background consumers cannot keep up with the web traffic. Increase consumer concurrency or add more Kafka partitions.

---

## ⚖️ Phase 4: Architectural A/B Testing
When considering a massive structural change (e.g., **Removing Kafka Transactions**), use this exact strategy to prove if the change was beneficial.

1. **Establish Control (Architecture A):** 
   Ensure your code is using Kafka Transactions. Run the `breakpoint` test and record the Ceiling (Max RPS) from Phase 2. Let's assume it breaks at **120 RPS**.
2. **Apply the Change (Architecture B):** 
   Modify your code to disable Kafka Transactions. Deploy the changes.
3. **Re-Test:** 
   Run the exact same `breakpoint` test again.
4. **Compare the Ceilings:** 
   Watch Grafana. Did the system survive up to 180 RPS instead of 120? 
5. **The Outcome:** 
   If the capacity numbers went up using the exact same hardware limits, you have scientifically proven the new architecture is more efficient. 

---

## 📏 Phase 5: Setting Operational Baselines
Once your architecture is locked in and fully optimized, take your final Maximum Capacity (e.g., `200 RPS`) and use it to calibrate your daily CI/CD testing profiles.

Open your `load-tests/k6-payment-flow.js` and configure your scenarios mathematically:

- **The Average Profile (`average`):** 
  Set this to **50% - 70%** of your ceiling (e.g., `100 RPS`). Run this on every pull request to ensure daily code changes don't accidentally degrade normal performance.
- **The Stress Profile (`stress`):** 
  Set this to **120% - 150%** of your ceiling (e.g., `250 RPS`). Run this occasionally to test how gracefully the system queues traffic, handles timeouts, and degrades under a sudden traffic spike.
