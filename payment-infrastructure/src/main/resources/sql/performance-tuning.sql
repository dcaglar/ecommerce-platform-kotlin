psql -h localhost -p 5432 -U payment

SELECT query, calls, mean_exec_time, total_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 3;


--current activities
SELECT pid, state, query, wait_event_type, wait_event
FROM pg_stat_activity;


--blocking chains

SELECT blocking.pid AS blocking_pid, blocked.pid AS blocked_pid, blocked.query AS blocked_query
FROM pg_locks blocking
JOIN pg_locks blocked
  ON blocking.locktype = blocked.locktype


EXPLAIN ANALYZE
SELECT count(*) FROM outbox_event WHERE status = 'NEW'

EXPLAIN ANALYZE SELECT * FROM outbox_event    WHERE status = 'NEW'ORDER BY created_at FOR UPDATE SKIP LOCKED  LIMIT 300;


--to reset statts
SELECT pg_stat_statements_reset();



--see currently running queries

SELECT pid,
       now() - pg_stat_activity.query_start AS duration,
       usename,
       state,
       query
FROM pg_stat_activity
WHERE state = 'active'
ORDER BY duration DESC;



--show locks and their details

SELECT
  bl.pid AS blocked_pid,
  ka.query AS blocking_query,
  a.query AS blocked_query,
  now() - a.query_start AS blocked_duration,
  ka.state AS blocking_state,
  a.state AS blocked_state
FROM pg_locks bl
JOIN pg_stat_activity a ON bl.pid = a.pid
JOIN pg_locks kl ON kl.locktype = bl.locktype AND kl.DATABASE IS NOT DISTINCT FROM bl.DATABASE
                 AND kl.relation IS NOT DISTINCT FROM bl.relation AND kl.page IS NOT DISTINCT FROM bl.page
                 AND kl.tuple IS NOT DISTINCT FROM bl.tuple AND kl.virtualxid IS NOT DISTINCT FROM bl.virtualxid
                 AND kl.transactionid IS NOT DISTINCT FROM bl.transactionid AND kl.classid IS NOT DISTINCT FROM bl.classid
                 AND kl.objid IS NOT DISTINCT FROM bl.objid AND kl.objsubid IS NOT DISTINCT FROM bl.objsubid
                 AND kl.pid != bl.pid
JOIN pg_stat_activity ka ON kl.pid = ka.pid
WHERE NOT bl.granted;


--Use this to get only the blocked/blocked-by relationships:
SELECT
    blocked_locks.pid     AS blocked_pid,
    blocked_activity.query AS blocked_query,
    blocking_locks.pid    AS blocking_pid,
    blocking_activity.query AS blocking_query
FROM pg_locks blocked_locks
JOIN pg_stat_activity blocked_activity
    ON blocked_activity.pid = blocked_locks.pid
JOIN pg_locks blocking_locks
    ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.DATABASE IS NOT DISTINCT FROM blocked_locks.DATABASE
    AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
    AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
    AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
    AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
    AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
JOIN pg_stat_activity blocking_activity
    ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted
ORDER BY blocked_pid;



--5️⃣ Show All Current Locks

SELECT l.pid, mode, relation::regclass, transactionid, granted, query
FROM pg_locks l
LEFT JOIN pg_stat_activity a ON l.pid = a.pid
ORDER BY granted, relation;



--get all long running queries (more than 5 seconds)
SELECT pid, now() - query_start AS duration, state, query
FROM pg_stat_activity
WHERE state = 'active'
  AND now() - query_start > interval '5 minutes'
ORDER BY duration DESC;




--⃣ See Current Table Bloat (Optional)
CREATE EXTENSION IF NOT EXISTS pgstattuple;


--How to Get Stats for All Partitions

--You need to run pgstattuple for each physical partition (the child tables):

SELECT
  relname,
  *
FROM (
  SELECT
    relname,
    (pgstattuple(relname)).*
  FROM (
    SELECT
      inhrelid::regclass AS relname
    FROM pg_inherits
    WHERE inhparent = 'outbox_event'::regclass
  ) AS partitions
) AS stats;







-- Run this for a specific table:
SELECT * FROM pgstattuple('outbox_event');



payment=# SHOW max_connections
payment-# ;
 max_connections
-----------------
 100
(1 row)

payment=# ALTER SYSTEM SET max_connections = 120;
ALTER SYSTEM
payment=# SELECT pg_reload_conf();
 pg_reload_conf
----------------
 t
(1 row)

payment=#








SELECT relname,
       pg_size_pretty(pg_table_size(relid)) AS table_size,
       pg_size_pretty(pg_indexes_size(relid)) AS indexes_size
FROM pg_catalog.pg_statio_user_tables
ORDER BY relname DESC;


SELECT * FROM pg_stat_bgwriter;
SELECT datname, blks_read, blks_hit
FROM pg_stat_database
order by datname;


1 — What the numbers are shouting
	1.	Backend writes exploded
Previous run: back-end pages ≈ 2 k
Latest run: back-end pages ≈ 12 k
→ Most dirty pages are being flushed by the session that issued the insert/update, not by the check-pointer.
	2.	Allocations shot up almost identically
buffers_alloc grew ≈ 10 k, matching the jump in buffers_backend.
→ Every new row is creating/dirtying a fresh page (typical when the B-tree keeps splitting).
	3.	Checkpoint share shrank
Even though buffers_checkpoint rose, backend writes rose faster; your workload is now random-IO dominated.
	4.	Checkpoint duration ballooned
256 s → 584 s cumulative write time. The check-pointer is desperately trying to flush, but backend traffic races ahead.

2 — Root cause in plain English

Inserts land on random leaf pages because the primary key on outbox_event is a UUID (plus updates hit an indexed status).
Every page touched becomes dirty once per insert/update, so Postgres can’t group them and has to flush in tiny random 8 kB chunks.
Result:
	•	huge buffers_backend
	•	growing checkpoint latency
	•	write-amplification exactly like the Spring I/O talk warned.

3 — How you’ll know the fix works

After switching to BIGINT/ULID → sequential clustering and dropping the standalone status index you should see for the same load pattern:
	•	buffers_backend ≪ buffers_checkpoint (ideally < 10 %).
	•	buffers_alloc roughly equals inserts but does not explode per update.
	•	checkpoint_write_time grows linearly, not exponentially, and each timed checkpoint finishes in seconds, not minutes.

Rule of thumb: when backend writes dominate, you’re paying UUID tax.

4 — Suggested next steps
	1.	Create the new clustered PK (numeric or time-sortable) on outbox_event; keep UUID as public_event_id if you still need it externally.
	2.	Drop or narrow the simple status index (the composite (status,created_at) already covers the queries you showed).
	3.	Retest the same load; capture a new snapshot; compare the deltas again.
	4.	Optional tuning after the schema fix: lower checkpoint_timeout (e.g. 5 min) and raise checkpoint_completion_target (0.9) so remaining checkpoints spill more gently.

Need an exact migration script or Kotlin ULID generator snippet? Just say the word.