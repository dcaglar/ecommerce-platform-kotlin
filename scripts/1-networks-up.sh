#!/bin/bash
set -e

echo "🔌 Creating Docker networks..."

docker network inspect messaging-net >/dev/null 2>&1 || docker network create messaging-net
docker network inspect payment-net >/dev/null 2>&1 || docker network create payment-net

echo "✅ Networks ready: messaging-net, payment-net"