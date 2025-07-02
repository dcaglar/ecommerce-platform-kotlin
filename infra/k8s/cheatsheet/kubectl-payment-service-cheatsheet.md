# Kubernetes kubectl Cheat Sheet for Payment Service Workflow

## Namespace Management

```
kubectl get namespaces
kubectl create namespace payment
kubectl delete namespace payment
```

## Apply/Update Resources

```
kubectl apply -f <file.yaml>                # Apply or update a resource
kubectl apply -f payment/payment-service-configmap.yaml
kubectl apply -f payment/payment-service-deployment.yaml
```

## Pod & Deployment Management

```
kubectl get pods -n payment                  # List pods in payment namespace
kubectl get deployments -n payment           # List deployments
kubectl describe pod <pod-name> -n payment   # Pod details
kubectl logs <pod-name> -n payment           # View pod logs
kubectl exec -it <pod-name> -n payment -- /bin/sh   # Shell into pod
kubectl delete pod <pod-name> -n payment     # Delete pod (will restart if managed by deployment)
kubectl rollout restart deployment/payment-service -n payment   # Restart deployment
```

## Service & Networking

```
kubectl get svc -n payment                   # List services
kubectl describe svc <svc-name> -n payment   # Service details
kubectl port-forward svc/payment-service 8080:8080 -n payment   # Forward local port to service
```

## ConfigMap & Secret Management

```
kubectl get configmap -n payment
kubectl describe configmap payment-service-config -n payment
kubectl edit configmap payment-service-config -n payment
kubectl delete configmap payment-service-config -n payment

kubectl get secret -n payment
kubectl describe secret <secret-name> -n payment
```

## Database, Kafka, Redis

```
kubectl apply -f payment/payment-db-statefulset.yaml
kubectl apply -f payment/payment-db-service.yaml
kubectl apply -f kafka/kafka-statefulset.yaml
kubectl apply -f kafka/kafka-service.yaml
kubectl apply -f redis/deployment.yaml
kubectl apply -f redis/redis-service.yaml
```

## Keycloak

```
kubectl apply -f keycloak/keycloak-db-statefulset.yaml
kubectl apply -f keycloak/keycloak-db-service.yaml
kubectl apply -f keycloak/deploy-keycloak-provisioner.sh
```

## Clean Up

```
kubectl delete deployment payment-service -n payment
kubectl delete statefulset payment -n payment
kubectl delete configmap payment-service-config -n payment
```

## Useful Shortcuts

```
kubectl get all -n payment                   # Get all resources in payment namespace
kubectl top pod -n payment                   # Show pod resource usage (metrics-server required)
kubectl get events -n payment                # Show recent events
```

---

**Tip:** Use `-n payment` to scope commands to the payment namespace.

**Tip:** Use `kubectl explain <resource>` for documentation on any resource type.

**Tip:** Use `kubectl apply -f <dir>` to apply all YAMLs in a directory.

