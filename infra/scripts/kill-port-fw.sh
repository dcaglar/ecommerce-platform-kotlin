#!/bin/bash

# Kill all kubectl port-forward processes
echo "Killing all kubectl port-forward processes..."
pkill -f "kubectl port-forward"
echo "All port-forwards stopped."