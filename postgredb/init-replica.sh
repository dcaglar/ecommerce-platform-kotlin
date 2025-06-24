#!/bin/bash
set -e

echo "Waiting for payment-db to be ready..."
for i in {1..60}; do
  pg_isready -h payment-db -p 5432 -U replica && break
  sleep 1
done
pg_isready -h payment-db -p 5432 -U replica || { echo "payment-db not available after 60 seconds, exiting."; exit 1; }


# If the data directory is empty, clone from primary
if [ ! -s "/var/lib/postgresql/data/PG_VERSION" ]; then
  echo "Cloning base backup from primary..."
  pg_basebackup -h payment-db -p 5432 -U replica -D /var/lib/postgresql/data -Fp -Xs -P -R -W <<EOF
replica_password
EOF
fi

# Start postgres as a replica (standby)
exec docker-entrypoint.sh postgres