#!/bin/bash
set -e

# This script is executed inside the primary container ON FIRST INIT ONLY.
# It creates the replica user and updates pg_hba.conf to allow replication.

psql -U "$POSTGRES_USER" <<-EOSQL
    CREATE ROLE replica WITH REPLICATION LOGIN PASSWORD 'replica_password';
EOSQL

# Allow replication connections from any host (relax for local/dev only!)
echo "host replication replica 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"