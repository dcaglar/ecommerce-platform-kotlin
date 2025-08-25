DO $$
DECLARE
    partition_time TIMESTAMP := '2025-06-20 23:00:00';  -- Start time (inclusive)
    end_time TIMESTAMP := '2025-06-21 14:00:00';        -- End time (inclusive)
    partition_name TEXT;
    create_sql TEXT;
BEGIN
    WHILE partition_time <= end_time LOOP
        partition_name := 'outbox_event_' || to_char(partition_time, 'YYYYMMDD_HH24MI');
        create_sql :=
            'CREATE TABLE IF NOT EXISTS ' || partition_name || ' PARTITION OF outbox_event ' ||
            'FOR VALUES FROM (''' || to_char(partition_time, 'YYYY-MM-DD HH24:MI:SS') || ''') ' ||
            'TO     (''' || to_char(partition_time + interval '30 minutes', 'YYYY-MM-DD HH24:MI:SS') || ''');';
        RAISE NOTICE '%', create_sql;  -- Print the SQL
        EXECUTE create_sql;
        partition_time := partition_time + interval '30 minutes';
    END LOOP;
END $$;