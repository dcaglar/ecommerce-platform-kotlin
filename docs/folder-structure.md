# 🗂️ Folder Structure — `ecommerce-platform-kotlin`

This document provides a concise overview of the project's multi-module structure, showing how each component fits into the payment, ledger, and observability pipeline.

---

```bash
ecommerce-platform-kotlin/
├── charts/                               # 🧭 Helm charts for Kubernetes deployment
│   ├── payment-service/                  # Helm chart for REST API deployable
│   │   ├── Chart.yaml
│   │   ├── templates/                    # Deployment, Service, HPA, ConfigMap, Secrets
│   │   └── values.yaml
│   ├── payment-consumers/                # Helm chart for Kafka worker deployable
│   │   ├── Chart.yaml
│   │   ├── templates/                    # Deployment, Service, HPA, ServiceMonitor
│   │   └── values.yaml
│   ├── payment-platform-config/          # Shared config, secrets, and values
│   │   ├── Chart.yaml
│   │   └── templates/                    # ConfigMap, Secrets
│   ├── helm-cheatsheet.md                # Helm CLI reference
│   └── step-by-step-env-management       # Environment setup guide
│
├── common/                               # 🔁 Shared contracts and utilities
│   ├── src/main/kotlin/com/dogancaglar/common/
│   │   ├── event/                        # EventEnvelope, Topics, EventMetadata, DomainEventEnvelopeFactory
│   │   ├── logging/                      # LogContext, GenericLogFields, MDC helpers
│   │   └── dto/                          # Shared DTOs (PaymentOrderStatusUpdateRequest)
│   ├── src/test/kotlin/                  # Unit tests for common utilities
│   └── pom.xml
│
├── docs/                                 # 📚 Documentation and architecture references
│   ├── architecture.md                   # High-level system overview (payment + ledger)
│   ├── architecture-internal-reader.md   # Deep dive into implementation details
│   ├── folder-structure.md               # (this file)
│   ├── how-to-start.md                   # Local setup and minikube deployment
│   ├── cheatsheet/                       # Quick reference guides
│   │   ├── connectivity.md               # Network troubleshooting
│   │   ├── docker-cli-cheatsheet.md      # Docker commands
│   │   ├── ELK.md                        # Elasticsearch/Logstash/Kibana
│   │   ├── kafka.md                      # Kafka CLI and troubleshooting
│   │   ├── kubernetes-cheatsheet.md       # K8s commands
│   │   ├── minikube-cheatsheet.md        # Minikube operations
│   │   └── nopan.md                      # NOPAN (No Pattern) anti-patterns
│   └── troubleshooting/                 # Issue resolution guides
│
├── filebeat/                             # 📋 Filebeat configuration for log shipping
│   └── filebeat.yml                      # ELK log forwarding config
│
├── infra/                                # 🧱 Local infrastructure setup & scripts
│   ├── helm-values/                      # Helm value overrides for local/CI/CD
│   │   ├── elasticsearch-values-local.yaml
│   │   ├── filebeat-values-local.yaml
│   │   ├── kafka-values-local.yaml
│   │   ├── kafka-exporter-values-local.yaml
│   │   ├── keycloak-values-local.yaml
│   │   ├── kibana-values-local.yaml
│   │   ├── monitoring-stack-values-local.yaml
│   │   ├── my-postgres-defaults.yaml
│   │   ├── payment-consumers-values-local.yaml
│   │   ├── payment-db-values-local.yaml
│   │   ├── payment-platform-config-values-local.yaml
│   │   ├── payment-service-values-local.yaml
│   │   ├── redis-values-local.yaml
│   │   └── ingress-values.yaml
│   ├── keycloak/                         # Keycloak realm + client provisioning scripts
│   │   ├── provision-keycloak.sh         # Provision realm and clients
│   │   ├── get-token.sh                  # OAuth2 token retrieval
│   │   └── output/                       # Generated secrets and logs
│   ├── scripts/                          # Helper scripts for infra bootstrapping
│   │   ├── bootstrap-minikube-cluster.sh # Start Minikube profile + metrics-server
│   │   ├── deploy-all-local.sh           # Deploy core stack (DB, Redis, Kafka, Keycloak)
│   │   ├── deploy-monitoring-stack.sh    # Prometheus + Grafana (kube-prometheus-stack)
│   │   ├── deploy-observability-stack.sh # ELK (Elasticsearch + Logstash + Kibana)
│   │   ├── deploy-elasticsearch.sh       # Elasticsearch deployment
│   │   ├── deploy-kibana.sh              # Kibana deployment
│   │   ├── deploy-filebeat.sh            # Filebeat deployment
│   │   ├── deploy-kafka-local.sh         # Kafka cluster deployment
│   │   ├── deploy-kafka-exporter-local.sh# Kafka lag metrics for Prometheus
│   │   ├── deploy-keycloak-local.sh      # Keycloak OAuth2 server
│   │   ├── deploy-payment-db-local.sh     # PostgreSQL database
│   │   ├── deploy-payment-platform-config.sh # Shared ConfigMaps/Secrets
│   │   ├── deploy-payment-service-local.sh   # Deploy REST API service
│   │   ├── deploy-payment-consumers-local.sh  # Deploy Kafka worker consumers
│   │   ├── deploy-redis-local.sh         # Redis deployment
│   │   ├── build-and-push-payment-service-docker-repo.sh    # Build service image
│   │   ├── build-and-push-payment-consumers-docker-repo.sh  # Build consumers image
│   │   ├── create-app-db-credentials-local.sh              # DB user creation
│   │   ├── add-consumer-lag-metric.sh    # Prometheus adapter for lag-based HPA
│   │   ├── port-forwarding.sh            # Port-forward Keycloak, Grafana, Prometheus, DB
│   │   ├── delete-all-local.sh           # Cleanup all resources
│   │   ├── delete-kafka-local.sh         # Kafka cleanup
│   │   ├── delete-minikube-cluster.sh    # Minikube cleanup
│   │   ├── delete-monitoring-stack.sh    # Monitoring cleanup
│   │   └── delete-payment-db-local.sh     # Database cleanup
│   ├── secrets/                          # Kubernetes secrets (local dev)
│   │   └── payment-platform-config-secrets-local.yaml
│   └── endpoints.json                    # Generated by deploy scripts (Ingress host info)
│
├── keycloak/                             # 🔐 Keycloak provisioning (duplicate of infra/keycloak)
│   ├── provision-keycloak.sh
│   ├── get-token.sh
│   └── output/
│
├── load-tests/                           # ⚙️ k6 load test scripts for throughput validation
│   ├── baseline-smoke-test.js            # Smoke test scenario
│   ├── readme.md                         # Load testing guide
│   └── startegy.md                       # Testing strategy document
│
├── payment-db/                           # 🗄️ PostgreSQL configuration
│   └── postgresql.conf                   # DB tuning parameters
│
├── prometheus/                           # 📈 Prometheus & Grafana configuration
│   ├── prometheus.yml                    # Prometheus scrape config
│   ├── grafana.ini                       # Grafana server config
│   ├── grafana-provisioning/             # Grafana datasources and dashboards
│   │   ├── datasources/                  # Prometheus datasource
│   │   └── dashboards/                   # Dashboard provisioning
│   └── grafana-sample-dashboards/        # Sample dashboard JSON files
│
├── payment-domain/                       # 💡 Core domain logic (pure Kotlin, no frameworks)
│   ├── src/main/kotlin/com/dogancaglar/paymentservice/domain/
│   │   ├── model/                        # Aggregates, Value Objects
│   │   │   ├── Payment.kt                # Payment aggregate root
│   │   │   ├── PaymentOrder.kt           # PaymentOrder aggregate
│   │   │   ├── PaymentOrderStatus.kt     # Status enum
│   │   │   ├── PaymentStatus.kt          # Payment status enum
│   │   │   ├── Amount.kt                 # Money value object
│   │   │   ├── PaymentOrderStatusCheck.kt # Status check request
│   │   │   ├── RetryItem.kt              # Retry queue item
│   │   │   ├── ScheduledPaymentOrderStatusRequest.kt
│   │   │   ├── ledger/                   # Double-entry accounting domain
│   │   │   │   ├── JournalEntry.kt       # Journal entry aggregate
│   │   │   │   ├── Posting.kt            # Debit/Credit postings
│   │   │   │   ├── Account.kt            # Account types and categories
│   │   │   │   └── JournalType.kt        # Transaction types (AUTH, CAPTURE, etc.)
│   │   │   └── vo/                       # Value Objects
│   │   │       ├── PaymentId.kt
│   │   │       ├── PaymentOrderId.kt
│   │   │       ├── BuyerId.kt
│   │   │       ├── SellerId.kt
│   │   │       ├── OrderId.kt
│   │   │       └── PaymentLine.kt
│   │   ├── event/                        # Domain events
│   │   │   ├── PaymentOrderCreated.kt
│   │   │   ├── PaymentOrderSucceeded.kt
│   │   │   ├── PaymentOrderFailed.kt
│   │   │   ├── PaymentOrderPspCallRequested.kt
│   │   │   ├── PaymentOrderPspResultUpdated.kt
│   │   │   ├── PaymentOrderStatusCheckRequested.kt
│   │   │   ├── PaymentOrderEvent.kt      # Base event interface
│   │   │   ├── PaymentEvent.kt
│   │   │   ├── LedgerEntriesRecorded.kt  # Ledger confirmation event
│   │   │   ├── LedgerEvent.kt
│   │   │   ├── OutboxEvent.kt            # Outbox event wrapper
│   │   │   └── EventMetadatas.kt         # Event metadata registry
│   │   ├── commands/                     # Command objects
│   │   │   ├── CreatePaymentCommand.kt
│   │   │   ├── LedgerRecordingCommand.kt
│   │   │   └── LedgerCommand.kt
│   │   ├── exception/                    # Domain exceptions
│   │   │   ├── PaymentDomainExceptions.kt
│   │   │   ├── PaymentValidationException.kt
│   │   │   └── PspUnavailableException.kt
│   │   └── util/                         # Domain utilities
│   │       ├── PaymentFactory.kt         # Payment aggregate factory
│   │       ├── PaymentOrderFactory.kt     # PaymentOrder aggregate factory
│   │       ├── PaymentOrderEventMapper.kt # Event ↔ Domain mapping
│   │       └── PSPStatusMapper.kt        # PSP status conversion
│   ├── src/test/kotlin/                  # Pure domain unit tests
│   └── pom.xml
│
├── payment-application/                  # 🧠 Application layer (use cases, orchestration)
│   ├── src/main/kotlin/com/dogancaglar/paymentservice/application/
│   │   ├── usecases/                     # Use case services
│   │   │   ├── CreatePaymentService.kt   # Payment creation orchestration
│   │   │   ├── ProcessPaymentService.kt  # PSP result processing + retry logic
│   │   │   ├── RecordLedgerEntriesService.kt # Ledger entry recording
│   │   │   ├── RequestLedgerRecordingService.kt # Ledger recording request
│   │   │   └── LedgerEntryFactory.kt     # LedgerEntry creation from JournalEntry
│   │   ├── constants/                    # Application constants
│   │   │   ├── IdNamespaces.kt           # ID generation namespaces
│   │   │   └── PaymentLogFields.kt       # Logging field constants
│   │   └── model/                        # Application models
│   │       └── LedgerEntry.kt            # Persistence model for ledger entries
│   ├── src/test/kotlin/                  # Application layer unit tests
│   └── pom.xml
│
├── payment-infrastructure/               # 🧩 Adapters & auto-configuration
│   ├── src/main/kotlin/com/dogancaglar/paymentservice/
│   │   ├── adapter/outbound/             # Outbound adapters (implementing ports)
│   │   │   ├── persistence/              # Persistence adapters
│   │   │   │   ├── entity/               # JPA entities
│   │   │   │   │   ├── PaymentEntity.kt
│   │   │   │   │   ├── PaymentOrderEntity.kt
│   │   │   │   │   ├── OutboxEventEntity.kt
│   │   │   │   │   ├── PaymentOrderStatusCheckEntity.kt
│   │   │   │   │   ├── JournalEntryEntity.kt
│   │   │   │   │   ├── LedgerEntryEntity.kt
│   │   │   │   │   └── PostingEntity.kt
│   │   │   │   ├── mybatis/              # MyBatis mappers and interfaces
│   │   │   │   │   ├── PaymentMapper.kt
│   │   │   │   │   ├── PaymentEntityMapper.kt
│   │   │   │   │   ├── PaymentOrderMapper.kt
│   │   │   │   │   ├── PaymentOrderEntityMapper.kt
│   │   │   │   │   ├── OutboxEventMapper.kt
│   │   │   │   │   ├── OutboxEventEntityMapper.kt
│   │   │   │   │   ├── PaymentOrderStatusCheckMapper.kt
│   │   │   │   │   ├── PaymentOrderStatusCheckEntityMapper.kt
│   │   │   │   │   ├── LedgerMapper.kt   # Ledger persistence mapper
│   │   │   │   │   ├── LedgerPersistenceMapper.kt
│   │   │   │   │   └── typehandler/      # Custom MyBatis type handlers
│   │   │   │   │       ├── PaymentOrderStatusTypeHandler.kt
│   │   │   │   │       └── UUIDTypeHandler.kt
│   │   │   │   ├── PaymentOutboundAdapter.kt        # Payment repository adapter
│   │   │   │   ├── PaymentOrderOutboundAdapter.kt   # PaymentOrder repository adapter
│   │   │   │   ├── OutboxBufferAdapter.kt           # Outbox event adapter
│   │   │   │   └── PaymentOrderStatusCheckAdapter.kt # Status check adapter
│   │   │   ├── kafka/                    # Kafka adapters
│   │   │   │   └── PaymentEventPublisher.kt # Transactional Kafka publisher
│   │   │   ├── redis/                    # Redis adapters
│   │   │   │   ├── PaymentRetryRedisCache.kt        # Retry ZSet cache
│   │   │   │   ├── PaymentRetryQueueAdapter.kt      # Retry queue port implementation
│   │   │   │   ├── PspResultRedisCacheAdapter.kt     # PSP result cache
│   │   │   │   └── RedisIdGeneratorPortAdapter.kt    # ID generator
│   │   │   └── serialization/            # Serialization adapters
│   │   │       └── JacksonSerializationAdapter.kt
│   │   ├── config/                       # Infrastructure configuration
│   │   │   └── kafka/                    # Kafka configuration
│   │   │       ├── KafkaProducerConfig.kt           # Producer factories
│   │   │       ├── KafkaTxExecutor.kt                # Transactional executor
│   │   │       ├── EventEnvelopeKafkaSerializer.kt   # Event serialization
│   │   │       ├── EventEnvelopeKafkaDeserializer.kt # Event deserialization
│   │   │       └── JacksonUtil.kt                   # Jackson utilities
│   │   ├── infrastructure/               # Infrastructure beans
│   │   │   └── PaymentInfrastructureAutoConfig.kt    # Spring Boot auto-config
│   │   ├── redis/                        # Redis configuration
│   │   │   └── RedisConfig.kt            # Redis connection factory
│   │   └── metrics/                      # Metrics configuration
│   │       ├── MetricNames.kt            # Metric name constants
│   │       ├── MetricHelper.kt           # Metrics helper utilities
│   │       ├── KafkaMetrics.kt           # Kafka-specific metrics
│   │       ├── RedisMetrics.kt           # Redis-specific metrics
│   │       └── JobMetrics.kt              # Job/scheduler metrics
│   ├── src/main/resources/
│   │   ├── mapper/                       # MyBatis XML mappers
│   │   │   ├── PaymentMapper.xml
│   │   │   ├── PaymentOrderMapper.xml
│   │   │   ├── OutboxEventMapper.xml
│   │   │   ├── PaymentOrderStatusCheckMapper.xml
│   │   │   └── LedgerMapper.xml          # Ledger entry persistence queries
│   │   ├── db/changelog/                 # Liquibase migrations
│   │   │   ├── changelog.master.xml      # Master changelog
│   │   │   ├── payment-changelog.xml
│   │   │   ├── payment-order-changelog.xml
│   │   │   ├── payment-outbox-changelog.xml
│   │   │   ├── 2025-10-27-ledger-entry-tables.xml # Ledger table creation
│   │   │   └── [other migration files]
│   │   ├── sql/                          # SQL scripts and utilities
│   │   │   ├── partitioning.sql         # Outbox partitioning utilities
│   │   │   ├── performance-tuning.sql    # DB performance settings
│   │   │   ├── checklist.txt             # Migration checklist
│   │   │   └── outbox/                   # Outbox partition management
│   │   │       ├── outbox-create-table-partition-index.sql
│   │   │       ├── create-missing-partitions.sql
│   │   │       ├── prunte-old-partitions.sql
│   │   │       └── remove-partitions.sql
│   │   └── META-INF/spring/              # Spring Boot auto-configuration
│   │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   ├── src/test/kotlin/                  # Infrastructure unit + integration tests
│   └── pom.xml
│
├── payment-service/                      # 🌐 REST API for initiating payments
│   ├── src/main/kotlin/com/dogancaglar/paymentservice/
│   │   ├── adapter/inbound/rest/         # REST adapters (inbound)
│   │   │   ├── PaymentController.kt      # REST endpoints
│   │   │   ├── PaymentControllerWebExceptionHandler.kt # Error handling
│   │   │   ├── PaymentService.kt        # REST service layer
│   │   │   ├── SecurityConfig.kt        # OAuth2 security configuration
│   │   │   ├── TraceFilter.kt            # Request tracing (traceId injection)
│   │   │   ├── dto/                      # REST DTOs
│   │   │   │   ├── PaymentRequestDTO.kt
│   │   │   │   ├── PaymentResponseDTO.kt
│   │   │   │   ├── PaymentOrderRequestDTO.kt
│   │   │   │   ├── PaymentOrderResponseDTO.kt
│   │   │   │   └── AmountDto.kt
│   │   │   └── mapper/                   # DTO ↔ Domain mapping
│   │   │       ├── PaymentRequestMapper.kt
│   │   │       └── AmountMapper.kt
│   │   ├── application/                  # Application configuration
│   │   │   ├── config/                   # Spring configuration
│   │   │   │   ├── PaymentServiceConfig.kt
│   │   │   │   ├── KafkaTopicsConfig.kt   # Kafka topic creation
│   │   │   │   ├── MultiDataSourceConfig.kt # DB datasources
│   │   │   │   ├── DBWriterTxManager.kt  # Transaction manager
│   │   │   │   ├── MyBatisFactoriesConfig.kt # MyBatis configuration
│   │   │   │   └── ThreadPoolConfig.kt   # Thread pool configuration
│   │   │   ├── maintenance/              # Maintenance jobs
│   │   │   │   ├── OutboxDispatcherJob.kt        # Outbox event dispatcher
│   │   │   │   ├── OutboxJobMyBatisAdapter.kt    # Outbox adapter
│   │   │   │   └── OutboxPartitionCreator.kt     # Partition management
│   │   │   └── IdResyncStartup.kt       # ID generator sync on startup
│   │   └── PaymentServiceApplication.kt  # Spring Boot application entry point
│   ├── src/main/resources/               # Application configuration
│   │   └── application-*.yml             # Profiles (local, kubernetes, docker, gke)
│   ├── src/test/kotlin/                  # Service layer unit tests
│   ├── Dockerfile                        # Container image definition
│   └── pom.xml
│
├── payment-consumers/                    # ⚙️ Async processing (Kafka-driven)
│   ├── src/main/kotlin/com/dogancaglar/paymentservice/
│   │   ├── port/inbound/consumers/       # Kafka consumers (inbound ports)
│   │   │   ├── PaymentOrderEnqueuer.kt           # Consumes PaymentOrderCreated → PSP call request
│   │   │   ├── PaymentOrderPspCallExecutor.kt    # Performs PSP calls (mocked latency)
│   │   │   ├── PaymentOrderPspResultApplier.kt   # Applies PSP results & publishes next events
│   │   │   ├── ScheduledPaymentStatusCheckExecutor.kt # Handles status check requests
│   │   │   ├── LedgerRecordingRequestDispatcher.kt # Routes finalized payments to ledger queue
│   │   │   ├── LedgerRecordingConsumer.kt        # Creates double-entry journals
│   │   │   ├── base/                             # Abstract consumer base classes
│   │   │   │   ├── AbstractKafkaConsumer.kt      # Base consumer with logging
│   │   │   │   ├── BaseSingleKafkaConsumer.kt   # Single record consumer
│   │   │   │   └── BaseBatchKafkaConsumer.kt    # Batch consumer
│   │   │   └── config/                          # Consumer configuration
│   │   │       ├── PaymentConsumerConfig.kt      # Consumer bean definitions
│   │   │       └── ConsumerThreadPoolConfig.kt  # Thread pool config
│   │   ├── adapter/outbound/             # Outbound adapters (consumers-specific)
│   │   │   └── psp/                      # PSP client adapter
│   │   │       ├── PaymentGatewayAdapter.kt      # PSP client port implementation
│   │   │       ├── PspSimulationProperties.kt    # PSP simulation configuration
│   │   │       ├── NetworkSimulator.kt            # Network latency simulation
│   │   │       └── PSPResponse.kt                # PSP response models
│   │   ├── service/                      # Service layer adapters
│   │   │   ├── RetryDispatcherScheduler.kt      # Redis retry queue → Kafka dispatcher
│   │   │   ├── PaymentOrderModificationTxAdapter.kt # PaymentOrder state updates
│   │   │   └── LedgerEntryTxAdapter.kt           # Ledger entry persistence adapter
│   │   └── consumers/                    # Dynamic consumer configuration
│   │       ├── KafkaTypedConsumerFactoryConfig.kt # Consumer factory configuration
│   │       └── DynamicKafkaConsumersProperties.kt # YAML-driven consumer config
│   ├── src/main/kotlin/com/dogancaglar/
│   │   └── PaymentConsumersApplication.kt       # Spring Boot application entry point
│   ├── src/main/resources/
│   │   └── application-*.yml             # Consumer application profiles
│   ├── src/test/kotlin/                  # Consumer unit tests
│   ├── Dockerfile                        # Container image definition
│   └── pom.xml
│
├── order-service/                         # 📦 Future: Order service module (placeholder)
│   └── pom.xml
│
├── shipment-service/                      # 🚚 Future: Shipment service module (placeholder)
│   └── pom.xml
│
├── wallet-service/                        # 💰 Future: Wallet service module (placeholder)
│   └── pom.xml
│
├── pom.xml                                # Maven parent POM (manages all modules)
├── mvnw                                   # Maven wrapper (Unix)
├── mvnw.cmd                               # Maven wrapper (Windows)
├── README.md                              # Project overview and diagrams
└── target/                                 # Maven build output (generated)

---

## 📝 Notes

### Module Dependencies

- **payment-service** → `payment-domain`, `payment-application`, `payment-infrastructure`, `common`
- **payment-consumers** → `payment-domain`, `payment-application`, `payment-infrastructure`, `common`
- **payment-application** → `payment-domain`, `common`
- **payment-infrastructure** → `payment-domain`, `common`
- **payment-domain** → `common` (minimal, pure domain)
- **common** → (no dependencies, shared utilities only)

### Key Design Points

- **Hexagonal Architecture**: Domain in center, adapters on edges
- **Ports & Adapters**: Domain defines ports (interfaces), infrastructure implements adapters
- **Event-Driven**: All state changes flow through Kafka events
- **Outbox Pattern**: Reliable DB → Kafka publishing
- **Double-Entry Ledger**: Journal entries with balanced postings
- **Idempotency**: Database-level unique constraints prevent duplicates
- **Observability**: Structured JSON logs, Prometheus metrics, distributed tracing

### Testing Structure

- **Unit Tests**: `*Test.kt` - pure logic, mocks only (MockK)
- **Integration Tests**: `*IntegrationTest.kt` - real dependencies (Testcontainers)
- **Test Separation**: Surefire for units, Failsafe for integration tests

---