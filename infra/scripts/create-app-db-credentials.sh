#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."

cd "$REPO_ROOT"

JOBFILE="$REPO_ROOT/infra/jobs/create-app-db-users.yaml"


kubectl apply -f "$JOBFILE"  -n payment