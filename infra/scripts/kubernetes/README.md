# Kubernetes App Management Scripts

This directory contains simple scripts to help you start, stop, and restart Kubernetes deployments (apps) in a given
namespace.

## Scripts

### 0.kubectl apply -k infra/k8s/overlays/local

Scales a deployment up to a specified number of replicas (default: 1).

**Usage:**

```
kubectl apply -k infra/k8s/overlays/local
```

### 1. k8s-start-app.sh

Scales a deployment up to a specified number of replicas (default: 1).

**Usage:**

```
./k8s-start-app.sh <deployment-name> <namespace> [replicas]
```

- `<deployment-name>`: Name of the deployment to start
- `<namespace>`: Kubernetes namespace
- `[replicas]`: (Optional) Number of replicas to scale to (default: 1)

**Example:**

```
./k8s-start-app.sh payment-service dev 2
```

---

### 2. k8s-stop-app.sh

Scales a deployment down to zero replicas (effectively stopping it).

**Usage:**

```
./k8s-stop-app.sh <deployment-name> <namespace>
```

- `<deployment-name>`: Name of the deployment to stop
- `<namespace>`: Kubernetes namespace

**Example:**

```
./k8s-stop-app.sh payment-service dev
```

---

### 3. k8s-restart-app.sh

Performs a rollout restart of a deployment (restarts all pods).

**Usage:**

```
./k8s-restart-app.sh <deployment-name> <namespace>
```

- `<deployment-name>`: Name of the deployment to restart
- `<namespace>`: Kubernetes namespace

**Example:**

```
./k8s-restart-app.sh payment-service dev
```

---

## Notes

- You must have `kubectl` installed and configured to access your cluster.
- Make sure the scripts are executable. If not, run: `chmod +x k8s-*.sh`
- These scripts are intended for use with Kubernetes deployments.

