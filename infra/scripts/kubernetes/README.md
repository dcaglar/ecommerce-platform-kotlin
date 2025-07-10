# Kubernetes Scripts Quick Start

This directory contains scripts to help you manage Kubernetes deployments for the ecommerce platform. The scripts are
designed for local development and can be adapted for other environments.

---

## 1. Deploy All Services to Local Cluster

To deploy all services and infrastructure to your local Kubernetes cluster (namespace: `payment`):

```
./deploy-k8s-overlay.sh local all payment
```

- `local`: Target environment (see overlays in `infra/k8s/overlays/`)
- `all`: Deploy all components (services + infra)
- `payment`: Namespace to use (default for local dev)

This will:

- Create the namespace if it doesn't exist
- Apply all manifests for all services and infrastructure
- Apply any secrets in the overlay

---

## 2. Port-Forward Infrastructure Services

To access infrastructure UIs (Keycloak, Kibana, Grafana, Prometheus) from your local machine, run:

```
./port-forward-infra.sh
```

- On macOS: Opens a new Terminal tab for each service
- On Linux: Runs each port-forward in the background

Default ports:

- Keycloak: http://localhost:8080
- Payment Service: http://localhost:8081
- Kibana: http://localhost:5601
- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090

You can also port-forward a single service:

```
./port-forward-single.sh <service-name> <local-port> <remote-port> <namespace>
```

Example:

```
./port-forward-single.sh keycloak 8080 8080 payment
```

---

## 3. Manage Deployments

### Scale a Deployment

```
./scale-k8s-deployment.sh <deployment> <namespace> <replicas>
```

Example:

```
./scale-k8s-deployment.sh payment-consumer payment 0
```

### Restart a Deployment

```
./restart-k8s-deployment.sh <deployment> <namespace>
```

Example:

```
./restart-k8s-deployment.sh payment-service payment
```

---

## 4. Delete All Resources in an Overlay

To delete all resources for a given overlay (e.g., clean up your cluster):

```
./delete-k8s-overlay.sh local all payment
```

---

## 5. Script Help

Most scripts support `-h` or `--help` for usage instructions:

```
./deploy-k8s-overlay.sh --help
```

---

## 6. Notes

- All scripts are designed for macOS and Linux. For Windows, use WSL or adapt as needed.
- Make scripts executable: `chmod +x <script>.sh`
- Overlays are in `infra/k8s/overlays/` and control which components are deployed.
- The `all` overlay includes everything for a full local stack.

---

For more details, see the script headers or source code.
