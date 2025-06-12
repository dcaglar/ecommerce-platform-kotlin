#!/bin/bash
echo "🚀 Starting full stack (network + infra + app)..."
bash ./network-up.sh
bash ./infra-up.sh
bash ./app-up.sh