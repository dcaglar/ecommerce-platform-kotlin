DO $$
DECLARE
    part record;
    new_count integer;
BEGIN
    FOR part IN
        SELECT inhrelid::regclass AS partition_name
        FROM pg_inherits
        WHERE inhparent = 'outbox_event'::regclass
    LOOP
        EXECUTE format('SELECT count(*) FROM %I WHERE status = %L', part.partition_name, 'NEW')
        INTO new_count;

        IF new_count = 0 THEN
            RAISE NOTICE 'Dropping partition: %', part.partition_name;
            -- Detach first (optional, for archiving)
            EXECUTE format('ALTER TABLE outbox_event DETACH PARTITION %I', part.partition_name);
            -- Drop table
            EXECUTE format('DROP TABLE %I', part.partition_name);
        END IF;
    END LOOP;
END $$;