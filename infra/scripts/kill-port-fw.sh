#!/bin/bash

# Kill all kubectl port-forward processes
echo "Killing all kubectl port-forward processes..."
ps -o pid,command | grep "port-forward"
pkill -f "port-forward"
echo "All port-forwards stopped."