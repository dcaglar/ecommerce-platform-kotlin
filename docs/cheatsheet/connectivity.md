Bash
kubectl top nodes
This will show you the exact physical CPU and Memory being consumed right now, which inherently includes all those <none> pods.

2. Check live usage of every single Pod:
   Bash
   kubectl top pods --all-namespaces
   This will print a real-time list of what those undefined pods are actually drawing from your Mac or server:

Plaintext
NAMESPACE     NAME                               CPU(cores)   MEMORY(bytes)
kube-system   local-path-provisioner-5db9...     2m           12Mi
payment       central-db-liquibase-rzw4c         1m           45Mi
How to proceed with your calculation?
For the Scheduler's perspective: Run kubectl describe nodes. Look at the Allocated resources section at the bottom. Kubernetes has already done the math for you, treating all <none> pods as 0.

For the Reality check: Run kubectl top nodes and compare the MEMORY% and CPU% to the allocated numbers. If your actual usage is much higher than your allocated requests, those <none> pods are the culprits eating up your unreserved capacity.


when on high traffic

dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin % kubectl describe nodes
Name:               orbstack
Roles:              control-plane
Labels:             beta.kubernetes.io/arch=arm64
beta.kubernetes.io/instance-type=k3s
beta.kubernetes.io/os=linux
kubernetes.io/arch=arm64
kubernetes.io/hostname=orbstack
kubernetes.io/os=linux
node-role.kubernetes.io/control-plane=true
node.kubernetes.io/instance-type=k3s
Annotations:        alpha.kubernetes.io/provided-node-ip: 192.168.139.2,fd07:b51a:cc66::2
flannel.alpha.coreos.com/backend-data: null
flannel.alpha.coreos.com/backend-type: host-gw
flannel.alpha.coreos.com/backend-v6-data: null
flannel.alpha.coreos.com/kube-subnet-manager: true
flannel.alpha.coreos.com/public-ip: 192.168.139.2
flannel.alpha.coreos.com/public-ipv6: fd07:b51a:cc66::2
k3s.io/hostname: orbstack
k3s.io/internal-ip: 192.168.139.2,fd07:b51a:cc66::2
k3s.io/node-args:
["server","--apiVersion","kubelet.config.k8s.io/v1beta1","--kind","KubeletConfiguration","--disable","metrics-server,traefik,coredns","--h...
k3s.io/node-config-hash: GJEGJ3KEVO5XIXKT2OPHRHJZLVXZXQQ2DSUAUVY3SORU4EGY5RFA====
k3s.io/node-env: {}
node.alpha.kubernetes.io/ttl: 0
volumes.kubernetes.io/controller-managed-attach-detach: true
CreationTimestamp:  Wed, 24 Jun 2026 08:44:59 +0200
Taints:             <none>
Unschedulable:      false
Lease:
HolderIdentity:  orbstack
AcquireTime:     <unset>
RenewTime:       Wed, 24 Jun 2026 09:39:20 +0200
Conditions:
Type             Status  LastHeartbeatTime                 LastTransitionTime                Reason                       Message
  ----             ------  -----------------                 ------------------                ------                       -------
MemoryPressure   False   Wed, 24 Jun 2026 09:35:49 +0200   Wed, 24 Jun 2026 08:44:59 +0200   KubeletHasSufficientMemory   kubelet has sufficient memory available
DiskPressure     False   Wed, 24 Jun 2026 09:35:49 +0200   Wed, 24 Jun 2026 08:44:59 +0200   KubeletHasNoDiskPressure     kubelet has no disk pressure
PIDPressure      False   Wed, 24 Jun 2026 09:35:49 +0200   Wed, 24 Jun 2026 08:44:59 +0200   KubeletHasSufficientPID      kubelet has sufficient PID available
Ready            True    Wed, 24 Jun 2026 09:35:49 +0200   Wed, 24 Jun 2026 08:45:00 +0200   KubeletReady                 kubelet is posting ready status
Addresses:
InternalIP:  192.168.139.2
InternalIP:  fd07:b51a:cc66::2
Hostname:    orbstack
Capacity:
cpu:                5
ephemeral-storage:  294714572Ki
memory:             10002932Ki
pods:               110
Allocatable:
cpu:                5
ephemeral-storage:  286698335417
memory:             10002932Ki
pods:               110
System Info:
Machine ID:                 82ddc538b9e95ae7c1d309201ccb5f9d
System UUID:                82ddc538b9e95ae7c1d309201ccb5f9d
Boot ID:                    abfff985-901a-4291-b084-052f9b6a1c4a
Kernel Version:             7.0.11-orbstack-00360-gc9bc4d96ac70
OS Image:                   OrbStack
Operating System:           linux
Architecture:               arm64
Container Runtime Version:  docker://29.4.0
Kubelet Version:            v1.34.8+orb1
Kube-Proxy Version:         
PodCIDR:                      192.168.194.0/25
PodCIDRs:                     192.168.194.0/25,fd07:b51a:cc66:a::/72
ProviderID:                   k3s://orbstack
Non-terminated Pods:          (24 in total)
Namespace                   Name                                                               CPU Requests  CPU Limits  Memory Requests  Memory Limits  Age
  ---------                   ----                                                               ------------  ----------  ---------------  -------------  ---
ingress-nginx               ingress-nginx-controller-ff4b855cc-m8hjd                           100m (2%)     0 (0%)      400Mi (4%)       400Mi (4%)     22m
keda                        keda-admission-webhooks-65f4c98bcb-xxq9p                           100m (2%)     1 (20%)     100Mi (1%)       1000Mi (10%)   30m
keda                        keda-operator-5fcb9dd8b6-skf97                                     100m (2%)     1 (20%)     100Mi (1%)       1000Mi (10%)   30m
keda                        keda-operator-metrics-apiserver-d644b5c76-qlx85                    100m (2%)     1 (20%)     100Mi (1%)       1000Mi (10%)   30m
kube-system                 coredns-58db975755-62bdr                                           100m (2%)     0 (0%)      70Mi (0%)        340Mi (3%)     54m
kube-system                 local-path-provisioner-5db9d5cbbb-vl7dz                            0 (0%)        0 (0%)      0 (0%)           0 (0%)         54m
kube-system                 svclb-ingress-nginx-controller-edb42915-2sztf                      0 (0%)        0 (0%)      0 (0%)           0 (0%)         22m
monitoring                  alertmanager-prometheus-stack-kube-prom-alertmanager-0             35m (0%)      0 (0%)      72Mi (0%)        72Mi (0%)      26m
monitoring                  prometheus-prometheus-stack-kube-prom-prometheus-0                 155m (3%)     0 (0%)      624Mi (6%)       624Mi (6%)     26m
monitoring                  prometheus-stack-grafana-999f67fbd-24rlw                           200m (4%)     0 (0%)      512Mi (5%)       512Mi (5%)     26m
monitoring                  prometheus-stack-kube-prom-operator-7bfdbcb-mrm4w                  30m (0%)      0 (0%)      96Mi (0%)        96Mi (0%)      26m
monitoring                  prometheus-stack-kube-state-metrics-76f65d75c-4vg8r                30m (0%)      0 (0%)      48Mi (0%)        48Mi (0%)      26m
monitoring                  prometheus-stack-prometheus-node-exporter-wrnfz                    15m (0%)      0 (0%)      24Mi (0%)        24Mi (0%)      26m
payment                     central-db-postgresql-0                                            100m (2%)     150m (3%)   128Mi (1%)       192Mi (1%)     7m
payment                     kafka-controller-0                                                 300m (6%)     200m (4%)   1000Mi (10%)     1200Mi (12%)   30m
payment                     kafka-exporter-prometheus-kafka-exporter-577676c8f6-b9wz6          50m (1%)      0 (0%)      64Mi (0%)        128Mi (1%)     21m
payment                     keycloak-0                                                         100m (2%)     0 (0%)      400Mi (4%)       400Mi (4%)     30m
payment                     keycloak-postgresql-0                                              100m (2%)     150m (3%)   128Mi (1%)       192Mi (1%)     30m
payment                     payment-central-relay-6cbbc5fc-zgm78                               200m (4%)     0 (0%)      700Mi (7%)       700Mi (7%)     14m
payment                     payment-consumers-0                                                200m (4%)     0 (0%)      700Mi (7%)       700Mi (7%)     11m
payment                     payment-edge-cell-0                                                50m (1%)      0 (0%)      128Mi (1%)       128Mi (1%)     15m
payment                     payment-edge-workers-0                                             150m (3%)     0 (0%)      500Mi (5%)       500Mi (5%)     14m
payment                     postgresql-exporter-prometheus-postgres-exporter-768697f585jmxv    50m (1%)      200m (4%)   64Mi (0%)        128Mi (1%)     21m
payment                     redis-master-0                                                     200m (4%)     0 (0%)      128Mi (1%)       128Mi (1%)     30m
Allocated resources:
(Total limits may be over 100 percent, i.e., overcommitted.)
Resource           Requests      Limits
  --------           --------      ------
cpu                2465m (49%)   3700m (74%)
memory             6086Mi (62%)  9512Mi (97%)
ephemeral-storage  100Mi (0%)    3Gi (1%)
Events:
Type     Reason                          Age                From                   Message
  ----     ------                          ----               ----                   -------
Normal   Starting                        54m                kube-proxy             
Normal   CertificateExpirationOK         54m                k3s-cert-monitor       Node and Certificate Authority certificates managed by k3s are OK
Normal   Starting                        54m                kubelet                Starting kubelet.
Warning  InvalidDiskCapacity             54m                kubelet                invalid capacity 0 on image filesystem
Normal   NodeHasSufficientMemory         54m (x3 over 54m)  kubelet                Node orbstack status is now: NodeHasSufficientMemory
Normal   NodeHasNoDiskPressure           54m (x3 over 54m)  kubelet                Node orbstack status is now: NodeHasNoDiskPressure
Normal   NodeHasSufficientPID            54m (x3 over 54m)  kubelet                Node orbstack status is now: NodeHasSufficientPID
Normal   NodeAllocatableEnforced         54m                kubelet                Updated Node Allocatable limit across pods
Normal   NodeReady                       54m                kubelet                Node orbstack status is now: NodeReady
Normal   Synced                          54m                cloud-node-controller  Node synced successfully
Normal   RegisteredNode                  54m                node-controller        Node orbstack event: Registered Node orbstack in Controller
Normal   NodePasswordValidationComplete  54m                k3s-supervisor         Deferred node password secret validation complete
dogancaglar@Dogans-MacBook-Pro ecommerce-platform-kotlin %                       