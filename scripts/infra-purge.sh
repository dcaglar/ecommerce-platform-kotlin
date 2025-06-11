#!/bin/bash
read -p "⚠️ WARNING: This will permanently delete ALL infra containers AND volumes. Proceed? (y/n): " confirm
if [[ "$confirm" =~ ^[Yy]$ ]]; then
    echo "🔥 Purging infra..."
    docker compose -f ../docker-compose.infra.yml down -v
    echo "✅ Infra purged."
else
    echo "❌ Aborted by user."
fi