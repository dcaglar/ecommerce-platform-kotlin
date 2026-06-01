# The Edge Watermark Pattern (T_Safe)

This document explains the distributed systems pattern used to achieve **Global Chronological Ordering** across entirely independent, decentralized Edge Nodes without introducing a central locking bottleneck.

## The Problem: Causal Consistency
In our Merchant-of-Record architecture, an `Authorization` event and a `Capture` event might occur on two completely different Edge Cells (due to load balancing). 
If Node A creates the Auth at `10:00:00`, and Node B creates the Capture at `10:00:05`, the Central Ledger *must* process the Auth before the Capture. 

However, because Node A and Node B are pushing events asynchronously to Central, Node B might forward its Capture event *before* Node A forwards its Auth event. If the Central processor immediately handles events as they arrive, it would process the Capture first, resulting in a fatal ledger error.

## The Solution: The T_Safe Watermark

To solve this, we use a decentralized Watermarking algorithm. 

### 1. The Edge Watermark Table
In the Central DB, there is a small table called `edge_watermarks`.
Each Edge Cell (Node) maintains a single row in this table.
When an Edge Cell forwards a batch of outbox events to Central, it updates its specific row with `forwarded_up_to = MAX(originated_at)`.
This tells Central: *"I, Node A, guarantee that I have forwarded all events that occurred on my machine up to 10:00:00."*

### 2. Calculating T_Safe
Before the Central Kafka Relayer processes any events, it calculates `T_Safe`:
`T_Safe = MIN(forwarded_up_to) FROM edge_watermarks`

`T_Safe` represents the global "safe line". If `T_Safe` is `10:00:00`, it means **every single active node in the cluster has successfully pushed all of its data up to 10:00:00.** 

### 3. Processing Events safely
The Central Relayer then queries the outbox:
`SELECT * FROM central_outbox WHERE originated_at <= T_Safe ORDER BY originated_at ASC`

Because it only processes events older than `T_Safe`, and sorts them by their true creation time, it is mathematically guaranteed to process the Auth (10:00:00) before the Capture (10:00:05), regardless of which node forwarded its data first!

---

## Graceful Decommissioning (The Deadman's Switch)

What happens if an Edge Cell is scaled down or crashes? Its watermark stops moving forward, meaning `T_Safe` gets permanently stuck, and global processing halts.

### The @PreDestroy Decommissioning Flow
To allow for Zero-Downtime Deployments, Edge Cells implement a strict Graceful Decommissioning flow:
1. Kubernetes sends `SIGTERM` (Scale down).
2. The Node's `LocalOutboxForwarderJob` enters a `while` loop, frantically flushing all remaining events from its 20Gi local disk to Central.
3. **ONLY IF** the local outbox is confirmed 100% empty, the node executes `DELETE FROM edge_watermarks WHERE edge_node_id = 'me'`.
4. The node shuts down peacefully.
5. Because its watermark is deleted, `T_Safe` ignores the node, and the rest of the cluster continues running at full speed.

### The 120-Second Safety Net
Kubernetes is configured with `terminationGracePeriodSeconds: 120`. If the node takes longer than 120 seconds to flush its data, Kubernetes executes a `SIGKILL` and forcefully assassinates the Java process.
- The node instantly dies.
- It **never reaches Step 3**, so the watermark is **not deleted**.
- The unforwarded events are perfectly safe on the persistent disk.
- Because the watermark remains, `T_Safe` gets stuck. The Central cluster mathematically refuses to process newer Capture events until an operator recovers the dead node and forwards the missing Auth events. 

This architecture guarantees that the system will **halt the entire world** rather than let a single transaction process out of order.
