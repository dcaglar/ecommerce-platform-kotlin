-- grant-replica-select.sql

-- Grant SELECT on all existing tables to replica
GRANT SELECT ON ALL TABLES IN SCHEMA public TO replica;

-- Ensure future tables also allow SELECT to replica
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO replica;