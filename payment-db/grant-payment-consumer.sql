-- Replace these variables as needed
\set new_user 'payment_consumer'
\set new_password 'payment_consumer'
\set db_name 'payment-db'

-- Create the new user
DO $$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles WHERE rolname = :'new_user'
   ) THEN
      EXECUTE format('CREATE USER %I WITH PASSWORD %L', :'new_user', :'new_password');
   END IF;
END
$$;

-- Grant privileges (adjust as needed)
GRANT CONNECT ON DATABASE :"db_name" TO :new_user;
GRANT USAGE ON SCHEMA public TO :new_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO :new_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO :new_user;

-- For future tables/sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO :new_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO :new_user;