
## 📝 Notes

```
kubectl get nodes -o custom-columns=NAME:.metadata.name,ALLOCATABLE:.status.allocatable.memory
kubectl get nodes -o custom-columns=NAME:.metadata.name,ALLOCATABLE:.status.allocatable.cpu   
```

Example
this is total allocatable memory/cpu on the node
Example output:NAME       ALLOCATABLE
minikube   11978768Ki

(NAME       ALLOCATABLE)

(minikube   7)


# # Describe get to see resource requests/limits on the node
```
kubectl describe $(kubectl get nodes -o name | head -n1) \
| sed -n '/Allocated resources:/,/Events:/p'
```

Example

Allocated resources:
(Total limits may be over 100 percent, i.e., overcommitted.)
Resource           Requests      Limits
  --------           --------      ------
cpu                4005m (57%)   11140m (159%)
memory             6504Mi (55%)  11302Mi (96%)
ephemeral-storage  50Mi (0%)     1Gi (0%)
hugepages-1Gi      0 (0%)        0 (0%)
hugepages-2Mi      0 (0%)        0 (0%)
hugepages-32Mi     0 (0%)        0 (0%)
hugepages-64Ki     0 (0%)        0 (0%






# #cpu/memorty resource request/limit on cpu/memory per pod instance(NOT ACTUAL USAGE)


```
kubectl get pods -A -o \
custom-columns="NAMESPACE:.metadata.namespace,NAME:.metadata.name,CPU_REQUEST:.spec.containers[*].resources.requests.cpu,MEM_REQUEST:.spec.containers[*].resources.requests.memory,CPU_LIMIT:.spec.containers[*].resources.limits.cpu,MEM_LIMIT:.spec.containers[*].resources.limits.memory"
```

```
kubectl get pods -n payment -o \
custom-columns="NAMESPACE:.metadata.namespace,NAME:.metadata.name,CPU_REQUEST:.spec.containers[*].resources.requests.cpu,MEM_REQUEST:.spec.containers[*].resources.requests.memory,CPU_LIMIT:.spec.containers[*].resources.limits.cpu,MEM_LIMIT:.spec.containers[*].resources.limits.memory" -n payment
```
Example
NAMESPACE       NAME                                                        CPU_REQUEST   MEM_REQUEST   CPU_LIMIT   MEM_LIMIT
ingress-nginx   ingress-nginx-controller-69c6c964ff-rzmms                   100m          90Mi          300m        256Mi
kube-system     coredns-674b8bbfcf-56xxh                                    100m          70Mi          <none>      170Mi
kube-system     etcd-minikube                                               100m          100Mi         <none>      <none>
kube-system     kube-apiserver-minikube                                     250m          <none>        <none>      <none>
kube-system     kube-controller-manager-minikube                            200m          <none>        <none>      <none>





# # Current live usage per pod in the node sort by memory or cpu
```
kubectl top pods -A --containers --sort-by=memory
kubectl top pods -n payment --containers --sort-by=memory
kubectl top pods -A --containers --sort-by=cpu
```


Example:
NAMESPACE       POD                                                         NAME                        CPU(cores)   MEMORY(bytes)   
payment         kafka-controller-0                                          kafka                       530m         1245Mi          
payment         payment-consumers-75c989db5-nqc2g                           payment-consumers           399m         574Mi           
payment         payment-consumers-75c989db5-bcbnk                           payment-consumers           398m         543Mi           
kube-system     kube-apiserver-minikube                                     kube-apiserver              260m         528Mi           
payment         payment-consumers-75c989db5-h2mql                           payment-consumers           437m         521Mi           
payment         payment-consumers-75c989db5-wn6kw                           payment-consumers           422m         490Mi           
payment         payment-consumers-75c989db5-n6qnx                           payment-consumers           373m         489Mi





# # get event log of kubecluster , what happened, scaleup,kill,restart etc

```
kubectl get events -A --sort-by=.lastTimestamp | tail -n 60

```

Example Output
kube-system     13m         Warning   Unhealthy                      pod/metrics-server-7fbb699795-nft6c                        Readiness probe failed: HTTP probe failed with statuscode: 500
payment         13m         Warning   Unhealthy                      pod/payment-consumers-75c989db5-nqc2g                      Startup probe failed: Get "http://10.244.0.34:8080/actuator/health/liveness": dial tcp 10.244.0.34:8080: connect: connection refused
payment         13m         Warning   Unhealthy                      pod/payment-consumers-75c989db5-bcbnk                      Startup probe failed: Get "http://10.244.0.36:8080/actuator/health/liveness": dial tcp 10.244.0.36:8080: connect: connection refused
payment         13m         Warning   Unhealthy                      pod/payment-consumers-75c989db5-w7k44                      Startup probe failed: Get "http://10.244.0.35:8080/actuator/health/liveness": dial tcp 10.244.0.35:8080: connect: connection refused
payment         12m         Warning   Unhealthy                      pod/kafka-controller-0                                     Liveness probe failed: command timed out: "pgrep -f kafka" timed out after 5s
monitoring      12m         Warning   Unhealthy                      pod/prometheus-stack-prometheus-node-exporter-rpm2r        Liveness probe failed: Get "http://192.168.49.2:9100/": context deadline exceeded (Client.Timeout exceeded while awaiting headers)
payment         8m10s       Warning   Unhealthy                      pod/payment-service-7dcdc89f9-jpxtm                        Startup probe failed: Get "http://10.244.0.37:9000/actuator/health/liveness": dial tcp 10.244.0.37:9000: connect: connection refused
payment         8m10s       Warning   Unhealthy                      pod/payment-db-postgresql-0                                Liveness probe failed: command timed out: "/bin/sh -c exec pg_isready -U \"postgres\" -d \"dbname=payment_db\" -h 127.0.0.1 -p 5432" timed out after 10s


# Is an External Metrics API registered?
kubectl get apiservices | grep external.metrics

# What external metrics are available?
kubectl get --raw /apis/external.metrics.k8s.io/v1beta1 | jq .

# Do you have a metrics adapter (KEDA or Prometheus Adapter)?
kubectl get deploy -A | egrep 'keda|adapter|metrics-api'

# Do you have a Kafka lag exporter?
kubectl get deploy -A | egrep 'kafka.*exporter|burrow'





dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin % helm template payment-consumers charts/payment-consumers \                                                     
-n payment \
-f infra/helm-values/payment-consumers-values-local.yaml  --debug > rendered.yaml

