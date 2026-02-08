# 📘 Spring Boot Capacity Planning & Load Testing Handbook
**For Spring Boot + Tomcat + HikariCP + k6**

---

## 1. Glossary: k6 Terms vs. Server Reality

Before calculating anything, understand how k6 metrics map to server resources.

| Term | In k6 (The Tester) | In Tomcat (The Server) |
| :--- | :--- | :--- |
| **VU (Virtual User)** | A worker loop. It sends a request, waits for a response, maybe sleeps, repeats. | **A Potential TCP Connection.** |
| **RPS (Reqs/Sec)** | Throughput. How many times `http.post` happens per second. | **The Load.** |
| **Response Time ($W$)** | `http_req_duration`. The time from sending to receiving. | **Thread Occupancy Time.** How long a thread is busy. |
| **Sleep ($Z$)** | `sleep(1)`. Time the VU does nothing. | **Idle Time.** The server does nothing for this user. |
| **Iteration** | One full cycle: `Request` + `Response` + `Sleep`. | N/A |

---

## 2. Baseline Measurements 📏

We base all calculations on the **Worst Observed Behavior** (P95/Max) from single-request tests, not the average.

* **Worst Case Response Time ($W$):** `0.6s` (600ms)
    * *Includes Stripe Latency + Network Overhead.*
* **Pure DB Transaction Time ($W_{db}$):** `0.02s` (20ms)
    * *Includes only SQL execution time (assuming no `@Transactional` around Stripe).*
* **Target Load:** `200`
    * *Can be defined as 200 VUs or 200 Req/s depending on the scenario.*

---

## 3. Scenarios & Calculations

### Scenario A: Real Humans (With Sleep) 🛌
*Simulates 200 humans clicking "Pay", waiting 1s, then clicking again.*

* **Math (The Duty Cycle):**
    * Active Time ($W$): 0.6s
    * Sleep Time ($Z$): 1.0s
    * Total Loop: 1.6s
    * **Duty Cycle:** $0.6 / 1.6 = \mathbf{37.5\%}$ (User is only active 37.5% of the time).
* **RPS Generated:** $200 \text{ VUs} / 1.6s = \mathbf{125 \text{ req/s}}$.
* **Tomcat Threads Needed ($N$):**
    * $N = 200 \text{ VUs} \times 0.375 = \mathbf{75 \text{ Threads}}$.

> **Config Recommendation:**
> * `server.tomcat.threads.max`: **150** (75 + Safety Buffer).

### Scenario B: Stress/Robots (NO Sleep) 🤖
*Simulates 200 aggressive robots or a DDOS attack. Often used for max capacity testing.*

* **Math (Greedy):**
    * Active Time ($W$): 0.6s
    * Sleep Time ($Z$): 0.0s
    * **Duty Cycle:** **100%**.
* **RPS Generated:** $200 \text{ VUs} / 0.6s = \mathbf{333 \text{ req/s}}$. *(Load Triples!)*
* **Tomcat Threads Needed ($N$):**
    * $N = 200 \text{ VUs} \times 1.0 = \mathbf{200 \text{ Threads}}$.

> **Config Recommendation:**
> * `server.tomcat.threads.max`: **300** (200 + Safety Buffer).

### Scenario C: Service-to-Service (Ramping Arrival Rate) 🎯 **(RECOMMENDED)**
*Simulates a Checkout Service sending exactly 200 requests/sec, regardless of user count.*

* **Math (Little's Law):**
    * Target RPS ($\lambda$): 200.
    * Response Time ($W$): 0.6s.
    * **Threads Needed ($L$):** $200 \times 0.6 = \mathbf{120 \text{ Threads}}$.

> **Config Recommendation:**
> * `server.tomcat.threads.max`: **250** (120 + Safety Buffer).

---

## 4. Database Connection Pool Calculation 🛢️

**Formula:** $PoolSize = RPS \times \text{TransactionDuration}$

### Case 1: Optimized Code (No `@Transactional` on Stripe) ✅
You only hold the connection during the DB updates.
* **Time ($W_{db}$):** 0.02s (20ms).
* **Calculation:** $200 \times 0.02 = \mathbf{4 \text{ Connections}}$.
* **Verdict:** Your current pool of **12** is **Sufficient**.

### Case 2: Unoptimized Code (`@Transactional` wraps Stripe) ❌
You hold the connection for the entire API call.
* **Time ($W$):** 0.6s (600ms).
* **Calculation:** $200 \times 0.6 = \mathbf{120 \text{ Connections}}$.
* **Verdict:** Your current pool of **12** will crash immediately.

> **Action:** If unsure, set pool to **30-50** to be safe.

---

## 5. Configuration Cheat Sheet (`application.yaml`)

Use these values for a production-ready system handling ~200 RPS.

```yaml
server:
  tomcat:
    threads:
      # Calculated: 120 threads needed for 200 RPS @ 0.6s latency.
      # Configured: 250 gives ~100% safety buffer for latency spikes.
      max: 250
      
      # Keep 50 threads warm to handle sudden bursts without lag.
      min-spare: 50

spring:
  datasource:
    hikari:
      # Calculated: 4 needed (optimistic) vs 120 needed (pessimistic).
      # Configured: 30 is a safe middle ground.
      maximum-pool-size: 30
      
      # Fail fast if pool is full. Don't wait 30s.
      connection-timeout: 3000