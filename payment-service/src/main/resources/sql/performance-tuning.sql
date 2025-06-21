-- Enable the pg_stat_statements extension (for tracking query stats)
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Enable the adminpack extension (for some admin features, optional)
CREATE EXTENSION IF NOT EXISTS adminpack;




--to get the top 10 slowest queries
SELECT
    query,
    calls,
    total_exec_time,
    mean_exec_time,
    rows,
    min_exec_time,
    max_exec_time
FROM pg_stat_statements
where query ilike '%outbox_event%' or query ilike '%payments%' or query ilike '%payment_orders%'
ORDER BY mean_exec_time DESC
LIMIT 10;


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





