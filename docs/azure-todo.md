# 🌩️ Azure (AKS) Migration TODOs

This document outlines the specific infrastructure and configuration changes required when moving this architecture from local Minikube to production Azure Kubernetes Service (AKS).

## 1. Local NVMe SSDs for the Edge Cell (Zero Latency)
Currently, the `payment-service` StatefulSet uses a `volumeClaimTemplate` that relies on the default storage class. In the cloud, default storage classes (like Azure Managed Disks) are network-attached, which introduces network latency on every database `fsync`.

**To achieve true bare-metal performance for the Postgres Sidecar:**
- [ ] Provision an AKS Node Pool using **Lsv2-series** or **Lasv3-series** Virtual Machines (these have physically attached NVMe disks on the motherboard).
- [ ] Install the `Local Path Provisioner` (or use Azure Ephemeral OS disks) on the cluster.
- [ ] Update the `storageClassName` in `charts/payment-service/values.yaml` to point to your new local provisioner.

## 2. Availability Zones (Fault Tolerance)
We have already added `topologySpreadConstraints` to the Helm charts to ensure Edge Cells spread across zones.
- [ ] When creating the AKS cluster, explicitly ensure you select **multiple Availability Zones** (e.g., Zone 1, 2, and 3) for your Node Pools. If the cluster is single-zone, the scheduling constraints cannot be satisfied.

## 3. Ingress & Load Balancing
Minikube uses `minikube tunnel` to simulate a LoadBalancer IP.
- [ ] Install a production Ingress Controller on AKS (e.g., **NGINX Ingress Controller** or **Azure Application Gateway Ingress Controller (AGIC)**).
- [ ] Bind the Ingress Controller to a Static Public IP address in Azure.
- [ ] Update your DNS provider to point your domain to the Public IP.

## 4. Configuration & Endpoints
The local scripts rely on `infra/endpoints.json` pointing to `127.0.0.1`.
- [ ] Update `infra/endpoints.json` to reflect your actual Azure domain names (e.g., `api.yourdomain.com`).
- [ ] Update the Keycloak provisioning scripts (`keycloak/provision-keycloak.sh`) to target the production Keycloak URL.
- [ ] Review the Resource Requests and Limits. The current `Guaranteed QoS` configuration reserves exactly 2.5 CPU cores per Edge Cell. Ensure your Azure VM sizes are large enough to pack these efficiently (e.g., a 16-core VM can fit 6 Edge Cells perfectly).
