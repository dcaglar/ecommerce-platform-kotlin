Resource Profiles
Specifying container dependencies such as ConfigMap, Secret, and vol‐
umes is straightforward. We need some more thinking and experimenta‐
tion for figuring out the resource requirements of a container. Compute
resources in the context of Kubernetes are defined as something that can
be requested by, allocated to, and consumed from a container. The re‐
sources are categorized as compressible (i.e., can be throttled, such as CPU
or network bandwidth) and incompressible (i.e., cannot be throttled, such
as memory).
Making the distinction between compressible and incompressible re‐
sources is important. If your containers consume too many compressibleresources such as CPU, they are throttled, but if they use too many incom‐
pressible resources (such as memory), they are killed (as there is no other
way to ask an application to release allocated memory).
Based on the nature and the implementation details of your application,
you have to specify the minimum amount of resources that are needed
(called requests) and the maximum amount it can grow up to (the
limits). Every container definition can specify the amount of CPU and
memory it needs in the form of a request and limit. At a high level, the
concept of requests/ limits is similar to soft/hard limits. For example,
similarly, we define heap size for a Java application by using the -Xms
and -Xmx command-line options.
The requests amount (but not limits) is used by the scheduler when
placing Pods to nodes. For a given Pod, the scheduler considers only
nodes that still have enough capacity to accommodate the Pod and all of
its containers by summing up the requested resource amounts. In that
sense, the requests field of each container affects where a Pod can be
scheduled or not. Example 2-3 shows how such limits are specified for a
Pod.
Example 2-3. Resource limits
apiVersion: v1
kind: Pod
metadata:
name: random-generator
spec:
containers:
- image: k8spatterns/random-generator:1.0
  name: random-generator
  resources:
  requests:
  cpu: 100m
  memory: 200Mi
  limits:
  memory: 200Mi
  Initial resource request for CPU and memory.Upper limit until we want our application to grow at max. We don’t
  specify CPU limits by intention.
  The following types of resources can be used as keys in the requests
  and limits specification:
  memory
  This type is for the heap memory demands of your application, in‐
  cluding volumes of type emptyDir with the configuration
  medium: Memory. Memory resources are incompressible, so con‐
  tainers that exceed their configured memory limit will trigger the
  Pod to be evicted; i.e., it gets deleted and recreated potentially on a
  different node.
  cpu
  The cpu type is used to specify the range of needed CPU cycles for
  your application. However, it is a compressible resource, which
  means that in an overcommit situation for a node, all assigned CPU
  slots of all running containers are throttled relative to their speci‐
  fied requests. Therefore, it is highly recommended that you set
  requests for the CPU resource but no limits so that they can
  benefit from all excess CPU resources that otherwise would be
  wasted.
  ephemeral-storage
  Every node has some filesystem space dedicated for ephemeral
  storage that holds logs and writable container layers. emptyDir
  volumes that are not stored in a memory filesystem also use
  ephemeral storage. With this request and limit type, you can speci‐
  fy the application’s minimal and maximal needs. ephemeral-
  storage resources are not compressible and will cause a Pod to be
  evicted from the node if it uses more storage than specified in its
  limit.
  hugepage-<size>
  Huge pages are large, contiguous pre-allocated pages of memory
  that can be mounted as volumes. Depending on your Kubernetes
  node configuration, several sizes of huge pages are available, like 2
  MB and 1 GB pages. You can specify a request and limit for howmany of a certain type of huge pages you want to consume (e.g.,
  hugepages-1Gi: 2Gi for requesting two 1 GB huge pages). Huge
  pages can’t be overcommitted, so the request and limit must be the
  same.
  Depending on whether you specify the requests, the limits, or both,
  the platform offers three types of Quality of Service (QoS):
  Best-Effort
  Pods that do not have any requests and limits set for its containers
  have a QoS of Best-Effort. Such a Best-Effort Pod is considered the
  lowest priority and is most likely killed first when the node where
  the Pod is placed runs out of incompressible resources.
  Burstable
  A Pod that defines an unequal amount for requests and limits
  values (and limits is larger than requests, as expected) are
  tagged as Burstable. Such a Pod has minimal resource guarantees
  but is also willing to consume more resources up to its limit
  when available. When the node is under incompressible resource
  pressure, these Pods are likely to be killed if no Best-Effort Pods
  remain.
  Guaranteed
  A Pod that has an equal amount of request and limit resources
  belongs to the Guaranteed QoS category. These are the highest-pri‐
  ority Pods and are guaranteed not to be killed before Best-Effort
  and Burstable Pods. This QoS mode is the best option for your appli‐
  cation’s memory resources, as it entails the least surprise and
  avoids out-of-memory triggered evictions.
  So the resource characteristics you define or omit for the containers have
  a direct impact on its QoS and define the relative importance of the Pod
  in the event of resource starvation. Define your Pod resource require‐
  ments with this consequence in mind.RECOMMENDATIONS FOR CPU AND MEMORY RESOURCES
  While you have many options for declaring the memory and CPU needs
  of your applications, we and others recommend the following rules:
  For memory, always set requests equal to limits.
  For CPU, set requests but no limits.
  See the blog post “For the Love of God, Stop Using CPU Limits on
  Kubernetes” for a more in-depth explanation of why you should not use
  limits for the CPU, and see the blog post “What Everyone Should Know
  About Kubernetes Memory Limits” for more details about the recom‐
  mended memory settings.
  Pod Priority
  We explained how container resource declarations also define Pods’ QoS
  and affect the order in which the Kubelet kills the container in a Pod in
  case of resource starvation. Two other related concepts are Pod priority
  and preemption. Pod priority allows you to indicate the importance of a
  Pod relative to other Pods, which affects the order in which Pods are
  scheduled. Let’s see that in action in Example 2-4.
  Example 2-4. Pod priority
  apiVersion: scheduling.k8s.io/v1
  kind: PriorityClass
  metadata:
  name: high-priority
  value: 1000
  globalDefault: false
  description: This is a very high-priority Pod class
---
apiVersion: v1
kind: Pod
metadata:
name: random-generator
labels:
env: random-generator
spec:
containers:- image: k8spatterns/random-generator:1.0
name: random-generator
priorityClassName: high-priority
The name of the priority class object.
The priority value of the object.
globalDefault set to true is used for Pods that do not specify a
priorityClassName. Only one PriorityClass can have
globalDefault set to true.
The priority class to use with this Pod, as defined in PriorityClass
resource.
We created a PriorityClass, a non-namespaced object for defining an inte‐
ger-based priority. Our PriorityClass is named high-priority and has a
priority of 1,000. Now we can assign this priority to Pods by its name as
priorityClassName: high-priority. PriorityClass is a mechanism
for indicating the importance of Pods relative to one another, where the
higher value indicates more important Pods.
Pod priority affects the order in which the scheduler places Pods on
nodes. First, the priority admission controller uses the
priorityClassName field to populate the priority value for new Pods.
When multiple Pods are waiting to be placed, the scheduler sorts the
queue of pending Pods by highest priority first. Any pending Pod is picked
before any other pending Pod with lower priority in the scheduling
queue, and if there are no constraints preventing it from scheduling, the
Pod gets scheduled.
Here comes the critical part. If there are no nodes with enough capacity
to place a Pod, the scheduler can preempt (remove) lower-priority Pods
from nodes to free up resources and place Pods with higher priority. As a
result, the higher-priority Pod might be scheduled sooner than Pods with
a lower priority if all other scheduling requirements are met. This algo‐
rithm effectively enables cluster administrators to control which Pods are
more critical workloads and place them first by allowing the scheduler to
evict Pods with lower priority to make room on a worker node for higher-priority Pods. If a Pod cannot be scheduled, the scheduler continues with
the placement of other lower-priority Pods.
Suppose you want your Pod to be scheduled with a particular priority but
don’t want to evict any existing Pods. In that case, you can mark a
PriorityClass with the field preemptionPolicy: Never. Pods assigned
to this priority class will not trigger any eviction of running Pods but will
still get scheduled according to their priority value.
Pod QoS (discussed previously) and Pod priority are two orthogonal fea‐
tures that are not connected and have only a little overlap. QoS is used
primarily by the Kubelet to preserve node stability when available com‐
pute resources are low. The Kubelet first considers QoS and then the
PriorityClass of Pods before eviction. On the other hand, the scheduler
eviction logic ignores the QoS of Pods entirely when choosing preemption
targets. The scheduler attempts to pick a set of Pods with the lowest prior‐
ity possible that satisfies the needs of higher-priority Pods waiting to be
placed.
When Pods have a priority specified, it can have an undesired effect on
other Pods that are evicted. For example, while a Pod’s graceful termina‐
tion policies are respected, the PodDisruptionBudget as discussed in
Chapter 10, “Singleton Service”, is not guaranteed, which could break a
lower-priority clustered application that relies on a quorum of Pods.
Another concern is a malicious or uninformed user who creates Pods
with the highest possible priority and evicts all other Pods. To prevent
that, ResourceQuota has been extended to support PriorityClass, and
higher-priority numbers are reserved for critical system-Pods that should
not usually be preempted or evicted.
In conclusion, Pod priorities should be used with caution because user-
specified numerical priorities that guide the scheduler and Kubelet about
which Pods to place or to kill are subject to gaming by users. Any change
could affect many Pods and could prevent the platform from delivering
predictable service-level agreements.Project Resources
Kubernetes is a self-service platform that enables developers to run ap‐
plications as they see suitable on the designated isolated environments.
However, working in a shared multitenanted platform also requires the
presence of specific boundaries and control units to prevent some users
from consuming all the platform’s resources. One such tool is
ResourceQuota, which provides constraints for limiting the aggregated re‐
source consumption in a namespace. With ResourceQuotas, the cluster
administrators can limit the total sum of computing resources (CPU,
memory) and storage consumed. It can also limit the total number of ob‐
jects (such as ConfigMaps, Secrets, Pods, or Services) created in a name‐
space. Example 2-5 shows an instance that limits the usage of certain re‐
sources. See the official Kubernetes documentation on Resource Quotas
for the full list of supported resources for which you can restrict usage
with ResourceQuotas.
Example 2-5. Definition of resource constraints
apiVersion: v1
kind: ResourceQuota
metadata:
name: object-counts
namespace: default
spec:
hard:
pods: 4
limits.memory: 5Gi
Namespace to which resource constraints are applied.
Allow four active Pods in this namespace.
The sum of all memory limits of all Pods in this namespace must
not be more than 5 GB.
Another helpful tool in this area is LimitRange, which allows you to set
resource usage limits for each type of resource. In addition to specifying
the minimum and maximum permitted amounts for different resource
types and the default values for these resources, it also allows you to con‐trol the ratio between the requests and limits, also known as the
overcommit level. Example 2-6 shows a LimitRange and the possible con‐
figuration options.
Example 2-6. Definition of allowed and default resource usage limits
apiVersion: v1
kind: LimitRange
metadata:
name: limits
namespace: default
spec:
limits:
- min:
  memory: 250Mi
  cpu: 500m
  max:
  memory: 2Gi
  cpu: 2
  default:
  memory: 500Mi
  cpu: 500m
  defaultRequest:
  memory: 250Mi
  cpu: 250m
  maxLimitRequestRatio:
  memory: 2
  cpu: 4
  type: Container
  Minimum values for requests and limits.
  Maximum values for requests and limits.
  Default values for limits when no limits are specified.
  Default values for requests when no requests are specified.
  Maximum ratio limit/request, used to specify the allowed overcom‐
  mit level. Here, the memory limit must not be larger than twice the
  memory request, and the CPU limit can be as high as four times the
  CPU request.Type can be Container, Pod, (for all containers combined), or
  PersistentVolumeClaim (to specify the range for a request per‐
  sistent volume).
  LimitRanges help control the container resource profiles so that no con‐
  tainers require more resources than a cluster node can provide.
  LimitRanges can also prevent cluster users from creating containers that
  consume many resources, making the nodes not allocatable for other con‐
  tainers. Considering that the requests (and not limits) are the prima‐
  ry container characteristic the scheduler uses for placing,
  LimitRequestRatio allows you to control the amount of difference be‐
  tween the requests and limits of containers. A big combined gap be‐
  tween requests and limits increases the chances of overcommitting
  on the node and may degrade application performance when many con‐
  tainers simultaneously require more resources than initially requested.
  Keep in mind that other shared node-level resources such as process IDs
  (PIDs) can be exhausted before hitting any resource limits. Kubernetes al‐
  lows you to reserve a number of node PIDs for the system use and ensure
  that they are never exhausted by user workloads. Similarly, Pod PID lim‐
  its allow a cluster administrator to limit the number of processes running
  in a Pod. We are not reviewing these in details here as they are set as
  Kubelet configurations options by cluster administrators and are not used
  by application developers.
  Capacity Planning
  Considering that containers may have different resource profiles in dif‐
  ferent environments, and a varied number of instances, it is evident that
  capacity planning for a multipurpose environment is not straightforward.
  For example, for best hardware utilization, on a nonproduction cluster,
  you may have mainly Best-Effort and Burstable containers. In such a dy‐
  namic environment, many containers are starting up and shutting down
  at the same time, and even if a container gets killed by the platform dur‐
  ing resource starvation, it is not fatal. On the production cluster, where
  we want things to be more stable and predictable, the containers may be
  mainly of the Guaranteed type, and some may be Burstable. If a container
  gets killed, that is most likely a sign that the capacity of the cluster should
  be increased.Table 2-1 presents a few services with CPU and memory demands.
  Table 2-1. Capacity planning example
  CPU
  Memory
  Memory
  Pod
  request
  request
  limit
  A 500 m 500 Mi 500 Mi 4
  B 250 m 250 Mi 1000 Mi 2
  C 500 m 1000 Mi 2000 Mi 2
  D 500 m 500 Mi 500 Mi 1
  Total 4000 m 5000 Mi 8500 Mi 9
  Instances
  Of course, in a real-life scenario, the more likely reason you are using a
  platform such as Kubernetes is that there are many more services to
  manage, some of which are about to retire, and some of which are still in
  the design and development phase. Even if it is a continually moving tar‐
  get, based on a similar approach as described previously, we can calcu‐
  late the total amount of resources needed for all the services per
  environment.
  Keep in mind that in the different environments, there are different num‐
  bers of containers, and you may even need to leave some room for au‐
  toscaling, build jobs, infrastructure containers, and more. Based on this
  information and the infrastructure provider, you can choose the most
  cost-effective compute instances that provide the required resources.

