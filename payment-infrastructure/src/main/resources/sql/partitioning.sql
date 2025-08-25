CREATE TABLE outbox_event (
    id uuid NOT NULL,
    event_type varchar(255) NOT NULL,
    aggregate_id varchar(255) NOT NULL,
    payload text NOT NULL,
    status varchar(50) NOT NULL,
    created_at timestamp NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE outbox_event_20250618_1730 PARTITION OF outbox_event
    FOR VALUES FROM ('2025-06-18 17:30:00') TO ('2025-06-18 18:00:00');

CREATE TABLE outbox_event_20250618_1800 PARTITION OF outbox_event
    FOR VALUES FROM ('2025-06-18 18:00:00') TO ('2025-06-18 18:30:00');

    CREATE TABLE outbox_event_20250618_1830 PARTITION OF outbox_event
        FOR VALUES FROM ('2025-06-18 18:30:00') TO ('2025-06-18 19:00:00');


    CREATE INDEX idx_outbox_event_createdat ON outbox_event (created_at);
    CREATE INDEX idx_outbox_event_status ON outbox_event (status);
    CREATE INDEX idx_outbox_status_createdat ON outbox_event (status, created_at);

--get the list of partitions and their estimated row counts

SELECT
  relname AS partition,
  n_live_tup AS estimated_rows
FROM pg_stat_user_tables
WHERE relname LIKE 'outbox_event_%'
ORDER BY relname;



payment=# select count(*),status from outbox_event group by status;