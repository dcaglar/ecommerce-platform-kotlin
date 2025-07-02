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

# Get all zones in the region
ZONES=$(gcloud compute zones list --filter="region:$(gcloud compute regions describe $REGION --format='value(selfLink)')" --format="value(name)")

# Delete Compute Engine VM instances in all zones of the region
echo "Deleting Compute Engine VM instances in region: $REGION ..."
for Z in $ZONES; do
  vms=$(gcloud compute instances list --project "$PROJECT_ID" --filter="zone:(https://www.googleapis.com/compute/v1/projects/$PROJECT_ID/zones/$Z)" --format="value(name)")
  if [ -z "$vms" ]; then
    echo "No VM instances found in zone $Z."
  else
    for vm in $vms; do
      echo "Deleting VM instance $vm in zone $Z ..."
      gcloud compute instances delete "$vm" --zone "$Z" --project "$PROJECT_ID" --quiet || true
    done
  fi
done

# Delete persistent disks in all zones of the region
echo "Deleting persistent disks in region: $REGION ..."
for Z in $ZONES; do
  disks=$(gcloud compute disks list --project "$PROJECT_ID" --filter="zone:(https://www.googleapis.com/compute/v1/projects/$PROJECT_ID/zones/$Z)" --format="value(name)")
  if [ -z "$disks" ]; then
    echo "No persistent disks found in zone $Z."
  else
    for disk in $disks; do
      echo "Deleting disk $disk in zone $Z ..."
      gcloud compute disks delete "$disk" --zone "$Z" --project "$PROJECT_ID" --quiet || true
    done
  fi
done

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