1. Baseline Test (Smoke Test)
   Start with a very low number of virtual users (e.g., 1–10).
   Ensure the system works as expected with no errors.
   Validate monitoring, logging, and alerting.
2. Gradual Ramp-Up (Step Load)
   Slowly increase the number of users or requests in stages (e.g., 10 → 50 → 100 → 200, etc.).
   Hold each stage for several minutes.
   Monitor key metrics: latency, error rate, CPU/memory, DB, etc.
   Identify bottlenecks or failures at each stage.
3. Peak Load Test
   Reach your expected peak traffic (e.g., what you expect on Black Friday).
   Hold for a realistic duration (e.g., 30–60 minutes).
   Watch for resource exhaustion, slowdowns, or errors.
4. Stress Test
   Push beyond your expected peak to find the breaking point.
   Useful for understanding system limits and failure modes.
5. Soak Test (Endurance)
   Run a moderate load for an extended period (hours).
   Detect memory leaks, resource leaks, or performance degradation over time.
6. Recovery Test
   After a failure or overload, reduce the load and observe how quickly the system recovers.

You should always start with the basics: test your system under high load while all dependencies (including the PSP) are
healthy. This helps you:
Establish a baseline for your system’s maximum capacity and normal behavior.
Identify bottlenecks, resource limits, and scaling issues in your own code and infrastructure.
Ensure your system is stable and resilient under expected peak traffic before introducing failures.

run like this:
3 virtual users for 2minutes not set default options

```bash
VUS=3 DURATION=2m k6 run load-tests/baseline-smoke-test.js

VUS=3 DURATION=2m k6 run load-tests/baseline-smoke-test.js

```