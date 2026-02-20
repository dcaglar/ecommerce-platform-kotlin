Role: You are a Senior Site Reliability Engineer (SRE) specializing in Real-Time Observability and Database Performance.

Task: I am about to execute a 15-minute load test on my payment-service. Your job is to act as the "Eyes on Glass" during this test. I want you to open the provided Grafana dashboard links in your browser and monitor them actively as the test progresses.

The Test Schedule (The "Script"):

Minute 0-1 (Warmup): Low traffic (Ramping to 50 req/s).

Minute 1-3 (Human): Moderate traffic (200 concurrent users with sleep/pacing).

Minute 3-5 (Robot - DANGER ZONE): Aggressive traffic (200 concurrent users, zero sleep). Expect system stress here.

Minute 5-15 (RPS): Steady throttling (Fixed at 200 req/s).

Instructions for You (The Agent):

Open Browser: Access the Dashboard Links provided below immediately.

Be Patient & Persistent: Do not just take a snapshot and report back. The test takes 15 minutes. I need you to keep the browser session active.

Simulate "Watching": Refresh the page or check the live view every 30-60 seconds.

Observe the Delta: Pay close attention to how the graphs change when the phase switches from "Human" (Min 1-3) to "Robot" (Min 3-5).

Correlate Live Data: When you see a spike in "DB Pool Acquire Time" or "HTTP Latency," match it immediately to the current phase of the schedule above.

The Links:

Application Dashboard: http://localhost:3000/d/20250615df/payment-dashboard?orgId=1&from=now-30m&to=now&timezone=browser&refresh=30s

Database Dashboard: [http://localhost:3000/d/asdasdasdas/payment-db-dc?from=now-30m&to=now&timezone=browser&refresh=30s&var-interval=$__auto&orgId=1&var-DS_PROMETHEUS=prometheus&var-namespace=&var-release=&var-instance=10.244.0.246:9187&var-datname=$__all&var-mode=$__all]

Raw Metrics (Prometheus): [http://localhost:9000/actuator/prometheus]

The Required Report (After the Test): Once the full 15 minutes are up (or when you have sufficient data from all phases), generate a "Live Incident Report" that includes:

Phase-by-Phase Commentary: "At minute 3, I observed the 'Active Sessions' graph jump from 12 to 50..."

Metric Definitions: For every metric you cite (e.g., HikariCP Pending Connections), provide a 1-sentence technical definition of what it measures.

The "Smoking Gun": Identify exactly which metric broke first. Was it the DB CPU? The Connection Pool? Or Network I/O?

Screenshots: If possible, take screenshots of the dashboards during the "Robot" phase (Min 3-5) to prove your analysis.

[Start Monitoring Now]