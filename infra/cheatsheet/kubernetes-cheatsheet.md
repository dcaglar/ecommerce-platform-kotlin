# Kubernetes Cheatsheet

## Basic Commands

```sh
# Get cluster info
kubectl cluster-info

# List all namespaces
kubectl get namespaces

# List all pods in  payment name space
kubectl get pods  -n payment

# List all services in a namespace
kubectl get svc -n payment

# Describe a pod
kubectl describe pod payment-service -n payment

# Get logs from a pod
kubectl logs payment-service -n payment

kubectl logs deployment/keycloak -n payment

# Port-forward a service to localhost
kubectl port-forward svc/<service-name> <local-port>:<service-port> -n <namespace>

# Apply a manifest
kubectl apply -f <file.yaml>

# Delete a manifest
kubectl delete -f <file.yaml>

# Get events in a namespace
kubectl get events -n <namespace>
```

## ConfigMaps & Secrets

```sh
# Create a ConfigMap from a file
kubectl create configmap <name> --from-file=<key>=<path/to/file> -n <namespace>

# Create a Secret from literal
kubectl create secret generic <name> --from-literal=<key>=<value> -n <namespace>

# Create a Secret from file
kubectl create secret generic <name> --from-file=<key>=<path/to/file> -n <namespace>

# View a Secret (base64 decode)
kubectl get secret <name> -n <namespace> -o jsonpath='{.data.<key>}' | base64 --decode
```

## Deployments & Rollouts

```sh
# Get deployments
kubectl get deployments -n <namespace>

# Rollout status
kubectl rollout status deployment/<deployment-name> -n <namespace>

# Restart a deployment
kubectl rollout restart deployment/<deployment-name> -n <namespace>

# Scale a deployment
kubectl scale deployment <deployment-name> --replicas=<num> -n <namespace>
```

## Troubleshooting

```sh


# Get pod details
kubectl describe pod <pod-name> -n <namespace>

# Get logs
kubectl logs <pod-name> -n <namespace>

kubectl exec -it -n payment filebeat-sbnm4  -- bash
kubectl exec -it <pod-name> -n <namespace> -- /bin/sh

# Get resource usage (if metrics-server is installed)
kubectl top pod -n <namespace>
```

## Namespaces

```sh
# Create a namespace
kubectl create namespace <namespace>

# Delete a namespace
kubectl delete namespace <namespace>
```

## Clean Up

```sh
# Delete all pods in a namespace
kubectl delete pods --all -n <namespace>

# Delete all resources from a manifest
kubectl delete -f <file.yaml>
```

## Contexts & Switching Clusters

```sh
# List all available contexts (clusters your kubectl can talk to)
kubectl config get-contexts

# Show the current context (the cluster kubectl is using)
kubectl config current-context

# Switch to a different context (e.g., minikube, docker-desktop, GKE, etc.)
kubectl config use-context <context-name>

# Why: Contexts let you easily switch between local (minikube, Docker Desktop) and cloud (GKE, EKS, etc.) clusters. Always check your context before applying manifests to avoid deploying to the wrong cluster.
```

## Cluster Info & Health

```sh
# Show cluster endpoints and status
kubectl cluster-info

# Why: Use this to verify your cluster is running and kubectl is connected to the right cluster.

# Get all system pods (Kubernetes control plane and add-ons)
kubectl get pods -n kube-system

# Why: Useful for troubleshooting cluster health and seeing if system components are running.
```

---

## ConfigMap Creation from File (with Explanation)

### Create a ConfigMap directly in the cluster from a file

```sh
kubectl create configmap payment-service-config \
  --from-file=application-kubernetes.yml=payment-service/src/main/resources/application-kubernetes.yml \
  -n payment
```

**Why:** This command uploads your local application-kubernetes.yml as a ConfigMap in the payment namespace. Use this
when you want to quickly update the config in your cluster from your local file.

### Generate a ConfigMap YAML manifest from a file (for version control)

```sh
kubectl create configmap payment-service-config \
  --from-file=application-kubernetes.yml=payment-service/src/main/resources/application-kubernetes.yml \
  -n payment \
  --dry-run=client -o yaml > infra/k8s/payment/configmap/payment-service-configmap.yaml
```

**Why:** This command generates a YAML manifest for your ConfigMap, which you can commit to your repo. This is best
practice for GitOps and reproducible deployments. You can then apply it with `kubectl apply -f`.

---

**Tip:** Use `kubectl explain <resource>` to get documentation for any resource type.
