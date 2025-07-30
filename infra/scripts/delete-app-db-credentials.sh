#!/bin/bash
set -euo pipefail

echo "🧹 Deleting app DB credentials..."
# TODO: Add commands to delete DB credentials

#Since i wil be deleteing database commands are not needed, this onei s jiust to delete it from pour kubernmetes anamespace
 kubectl delete jobs.batch -n payment create-app-db-users