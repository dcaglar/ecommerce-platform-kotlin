DO $$
DECLARE
    part RECORD;
    new_count INTEGER;
    part_end_time TIMESTAMP;
    -- Calculate the end time of the *current* partition window
    now_time TIMESTAMP := now();
    curr_window_start TIMESTAMP;
    curr_window_end TIMESTAMP;
BEGIN
    -- Calculate current window start and end (assuming 30min window)
    curr_window_start := date_trunc('hour', now_time) + INTERVAL '30 minutes' * floor(date_part('minute', now_time) / 30);
    curr_window_end := curr_window_start + INTERVAL '30 minutes';

    FOR part IN
        SELECT inhrelid::regclass AS partition_name
        FROM pg_inherits
        WHERE inhparent = 'outbox_event'::regclass
    LOOP
        -- Extract the partition end time from its name (assumes name like outbox_event_YYYYMMDD_HHMM)
        part_end_time := to_timestamp(
            substring(part.partition_name::text from 'outbox_event_(\d{8}_\d{4})'),
            'YYYYMMDD_HH24MI'
        ) + INTERVAL '30 minutes';

        -- Only prune partitions *strictly before* the current window
        IF part_end_time <= curr_window_start THEN
            EXECUTE format('SELECT count(*) FROM %I WHERE status = %L', part.partition_name, 'NEW') INTO new_count;
            IF new_count = 0 THEN
                RAISE NOTICE 'Dropping partition: %', part.partition_name;
                EXECUTE format('ALTER TABLE outbox_event DETACH PARTITION %I', part.partition_name);
                EXECUTE format('DROP TABLE %I', part.partition_name);
            END IF;
        END IF;
    END LOOP;
END $$;