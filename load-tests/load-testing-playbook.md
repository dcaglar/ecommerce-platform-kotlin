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
This phase discovers exactly what your current hardware configuration can handle before it collapses.

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
