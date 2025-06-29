DO $$
DECLARE
    partition_time TIMESTAMP := '2025-06-18 03:30:00';  -- Start time (inclusive)
    end_time TIMESTAMP := '2025-06-19 15:30:00';        -- End time (inclusive)
    partition_name TEXT;
    drop_sql TEXT;
BEGIN
    WHILE partition_time <= end_time LOOP
        partition_name := 'outbox_event_' || to_char(partition_time, 'YYYYMMDD_HH24MI');
        drop_sql := 'DROP TABLE IF EXISTS ' || partition_name || ' CASCADE;';
        RAISE NOTICE '%', drop_sql;  -- Print the SQL command (optional, for logging)
        EXECUTE drop_sql;
        partition_time := partition_time + interval '30 minutes';
    END LOOP;
END $$;