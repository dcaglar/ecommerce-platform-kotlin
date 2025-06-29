#!/bin/bash
set -e

# Set your project ID here or export it before running script:
PROJECT_ID="${PROJECT_ID:-ecommerce-platform-dev}"
REGION="${REGION:-europe-west4}"
ZONE="${ZONE:-europe-west4-a}"

echo "Starting cleanup for project: $PROJECT_ID"

# Delete GKE clusters
echo "Deleting GKE clusters..."
clusters=$(gcloud container clusters list --project "$PROJECT_ID" --format="value(name,location)")
if [ -z "$clusters" ]; then
  echo "No GKE clusters found."
else
  while read -r cluster location; do
    echo "Deleting cluster $cluster in $location..."
    gcloud container clusters delete "$cluster" --zone "$location" --project "$PROJECT_ID" --quiet || true
  done <<< "$clusters"
fi

# Delete Artifact Registry repositories
echo "Deleting Artifact Registry repositories..."
repos=$(gcloud artifacts repositories list --project "$PROJECT_ID" --location "$REGION" --format="value(name)")
if [ -z "$repos" ]; then
  echo "No artifact repositories found."
else
  for repo in $repos; do
    echo "Deleting artifact repository $repo..."
    gcloud artifacts repositories delete "$repo" --location "$REGION" --project "$PROJECT_ID" --quiet || true
  done
fi

# Delete Cloud SQL instances
echo "Deleting Cloud SQL instances..."
instances=$(gcloud sql instances list --project "$PROJECT_ID" --format="value(name)")
if [ -z "$instances" ]; then
  echo "No Cloud SQL instances found."
else
  for instance in $instances; do
    echo "Deleting Cloud SQL instance $instance..."
    gcloud sql instances delete "$instance" --project "$PROJECT_ID" --quiet || true
  done
fi

# Delete Compute Engine VM instances
echo "Deleting Compute Engine VM instances..."
vms=$(gcloud compute instances list --project "$PROJECT_ID" --zones "$ZONE" --format="value(name)")
if [ -z "$vms" ]; then
  echo "No VM instances found."
else
  for vm in $vms; do
    echo "Deleting VM instance $vm..."
    gcloud compute instances delete "$vm" --zone "$ZONE" --project "$PROJECT_ID" --quiet || true
  done
fi

# Delete persistent disks (zonal)
echo "Deleting persistent disks..."
disks=$(gcloud compute disks list --project "$PROJECT_ID" --zones "$ZONE" --format="value(name)")
if [ -z "$disks" ]; then
  echo "No persistent disks found."
else
  for disk in $disks; do
    echo "Deleting disk $disk..."
    gcloud compute disks delete "$disk" --zone "$ZONE" --project "$PROJECT_ID" --quiet || true
  done
fi

# Release static external IP addresses
echo "Releasing static external IPs..."
ips=$(gcloud compute addresses list --project "$PROJECT_ID" --regions "$REGION" --format="value(name)")
if [ -z "$ips" ]; then
  echo "No static IP addresses found."
else
  for ip in $ips; do
    echo "Releasing static IP $ip..."
    gcloud compute addresses delete "$ip" --region "$REGION" --project "$PROJECT_ID" --quiet || true
  done
fi

echo "Cleanup complete."