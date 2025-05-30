# Architecture Overview: ecommerce-platform-kotlin

## Introduction

This document describes the architectural design and principles behind the `ecommerce-platform-kotlin` project, focusing
on scalability, resilience, and modularity.

---

## Architectural Principles

## Domain-Driven Design (DDD) and Hexagonal Architecture in This Project

### Domain-Driven Design (DDD)

- **Domain:**  
  The core business logic lives in the domain layer. This includes entities like `Payment`, `PaymentOrder`, domain
  exceptions, value objects, and domain events. The domain expresses the business rules, invariants, and behaviors
  without being polluted by technical concerns.

- **Bounded Contexts:**  
  Your modules (e.g., `payment-service`, `order-service`, `wallet-service`) represent distinct bounded contexts, each
  owning its own domain model and logic. This separation prevents mixing concepts and promotes clear responsibility.

- **Ubiquitous Language:**  
  The code, event names, and DTOs reflect domain concepts directly (e.g., `PaymentOrderCreated`), ensuring clear
  communication between technical and domain experts.

- **Ports and Adapters:**  
  The domain exposes interfaces (ports) like `PaymentOrderOutboundPort` or `IdGeneratorPort`. These define contracts for
  infrastructure interactions without leaking implementation details.

---

### Hexagonal Architecture (aka Ports and Adapters)

- **Core Domain:**  
  The `domain` package forms the inner hexagon, containing pure business logic isolated from external systems.

- **Ports:**  
  Interfaces defined in the domain layer that specify required operations for persistence, messaging, retry queues, and
  external services.

- **Adapters:**  
  Implementations of ports live in the adapter layer:
    - Persistence adapters (e.g., `JpaPaymentOutboundAdapter`) handle DB interactions.
    - Messaging adapters manage Kafka producers and consumers.
    - Redis adapters handle caching, retry queues, and ID generation.
    - Outbox pattern adapters manage reliable event dispatching.

- **Configuration Layer:**  
  External configuration and wiring (e.g., Spring Beans, Kafka configs) live in the `config` package, keeping
  infrastructure details outside the domain.

- **Application Layer:**  
  Contains use-case orchestration, event handling, and service logic (`PaymentService`, event mappers). It bridges
  domain model operations and external interactions.

---

### Benefits in the Project

- **Loose Coupling:**  
  Changes in infrastructure (e.g., swapping Redis or Kafka clients) don’t impact domain logic.

- **Testability:**  
  Domain logic can be tested in isolation using mocks of ports.

- **Clear Boundaries:**  
  Enforced separation helps avoid domain leakage and mixing technical details in business code.

- **Event-Driven Flow:**  
  The hexagonal design enables seamless event choreography between bounded contexts via Kafka, with traceability
  embedded.

### Event-Driven Architecture

- Kafka is used as the backbone for asynchronous communication.
- Events wrapped in `EventEnvelope` carry metadata (`traceId`, `parentEventId`).
- Ensures traceability and loose coupling.

### Observability

- Structured JSON logs with context propagation.
- Use of MDC for correlating requests and events.
- Integration with Elasticsearch and Kibana for search and visualization.
- Planned addition of Prometheus and Micrometer for metrics.

### Resilience Patterns

- Retry with exponential backoff using Redis ZSet.
- Scheduled status checks stored in PostgreSQL.
- Dead Letter Queue (DLQ) for failed events.
- Redis-backed ID generation with state synchronization.

---

## Component Overview

### payment-service

- Processes payment requests for multi-seller orders.
- Creates Payment and PaymentOrder aggregates.
- Emits and consumes domain events via Kafka.
- Integrates with mock PSP with latency and failure simulations.
- Supports retry, status polling, and DLQ mechanisms.

### common

- Contains shared models, event envelopes, and logging utilities.

### Future Modules

- order-service: emits order-created events.
- wallet-service: manages seller balances.
- shipment-service: handles delivery workflows.

---

## Deployment Architecture

### Local Development

- Docker Compose orchestrates all required services:
    - PostgreSQL (payment-db, keycloak-db)
    - Kafka + Zookeeper
    - Redis + RedisInsight
    - Elasticsearch + Kibana + Filebeat
    - Keycloak (optional)
    - Payment-service backend

### Kubernetes Deployment (Planned)

- Single Kubernetes cluster with namespaces for:
    - auth (Keycloak)
    - payment (payment-service, payment-db, Redis)
    - messaging (Kafka, Zookeeper)
    - observability (Elasticsearch, Kibana, Filebeat)

- Use node affinity to colocate Redis and PostgreSQL.
- Use PersistentVolumeClaims for durable storage.
- Deploy Filebeat as a DaemonSet for log shipping.
- Configure Horizontal Pod Autoscaling (HPA) for payment-service.

---

## Observability & Logging Flow

- Application logs contain structured JSON with `traceId`, `eventId`, `aggregateId`, `parentEventId`.
- Logs are collected by Filebeat running on each node.
- Filebeat ships logs to Elasticsearch.
- Kibana provides dashboards and search interfaces for troubleshooting by `publicPaymentId` or `publicPaymentOrderId`.

---

## Kubernetes Deployment Plan

### Cluster Setup

- Choose environment: Local (minikube, Docker Desktop), Cloud (GKE, EKS, AKS)
- Create a cluster with minimal nodes, scalable node pools

### Namespace Strategy

- Separate namespaces for logical grouping:
    - `auth` (optional, for Keycloak)
    - `payment` (payment-service backend, payment-db, Redis)
    - `messaging` (Kafka, Zookeeper)
    - `observability` (Elasticsearch, Kibana, Filebeat)

### Node Pools and Affinity

- Define node pools labeled by role:
    - `role=db` for PostgreSQL and Redis (ID generation colocated for latency)
    - `role=kafka` for Kafka and Zookeeper
    - `role=app` for payment-service pods
    - `role=observability` for ELK stack pods

- Use Pod affinity/anti-affinity and node selectors to ensure colocated services for low latency and separation of
  concerns

### Persistent Storage

- Use PersistentVolumeClaims (PVCs) for all stateful services (Postgres, Redis, Kafka where needed)
- Align PVCs with cloud provider storage classes or local storage options

### Deployment Objects

- Use StatefulSets for stateful services (Postgres, Redis, Kafka)
- Use Deployments for stateless services (payment-service, Keycloak, Kafka UI, RedisInsight)
- Deploy Filebeat as a DaemonSet for cluster-wide log collection

### Networking and Service Discovery

- Use Kubernetes Services for stable internal access
- Leverage DNS names like `redis.payment.svc.cluster.local` for intra-cluster communication

### CI/CD Pipelines

- Build automated pipelines to:
    - Build Docker images
    - Push images to container registry
    - Deploy manifests to Kubernetes cluster

### Observability

- Set up Filebeat DaemonSet for log shipping to Elasticsearch
- Configure Kibana dashboards for structured logs
- Plan to add Prometheus and Grafana monitoring dashboards and alerts

### Scaling and Resilience

- Use Horizontal Pod Autoscalers for payment-service based on CPU/memory or custom metrics
- Implement node autoscaling policies (if supported by cluster)
- Prepare for disaster recovery strategies (backups, multi-zone clusters)

---

## Roadmap

1. Complete structured logging and ELK stack setup.
2. Add Elasticsearch read model for payment queries.
3. Build monitoring dashboards and basic metrics.
4. Build Kubernetes CI/CD pipelines.
5. Implement node affinity and resource management.
6. Add alerting and advanced monitoring.
7. Build dummy wallet and shipment services.
8. Harden retry and DLQ handling.
9. Add OAuth2 security to all APIs.
10. Implement scheduled PSP status polling.

---

## References

- Domain-Driven Design by Eric Evans
- Kubernetes documentation
- Spring Boot and Kafka guides
- Elasticsearch and Kibana tutorials

---

*Document maintained by Doğan Çağlar.*
