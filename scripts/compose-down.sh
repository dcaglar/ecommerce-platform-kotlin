#!/bin/bash
echo "🛑 Stopping full stack (app + infra)..."
bash ./app-down.sh
bash ./infra-down.sh
