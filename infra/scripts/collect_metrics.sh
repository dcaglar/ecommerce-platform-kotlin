#!/bin/bash

# Get the project root directory
PROJECT_ROOT="/Users/dogancaglar/IdeaProjects/ecommerce-platform-kotlin"

# Initial timestamp for the filename (ddmmyyyy:hh:mm:ss)
FILE_TIMESTAMP=$(date +"%d%m%Y:%H:%M:%S")
OUTPUT_FILE="${PROJECT_ROOT}/prom_${FILE_TIMESTAMP}.txt"

echo "Starting metric collection..."
echo "Output file: ${OUTPUT_FILE}"
echo "Press [CTRL+C] to stop."

while true; do
    # Current time for the log entry
    CURRENT_TIME=$(date +"%d%m%Y:%H:%M:%S")
    
    # Write time, then metrics, then separator
    echo "TIME: $CURRENT_TIME" >> "$OUTPUT_FILE"
    curl -s http://localhost:9000/actuator/prometheus >> "$OUTPUT_FILE"
    echo "-------------------------------------------" >> "$OUTPUT_FILE"
    
    # Wait for 2 seconds
    sleep 2
done
