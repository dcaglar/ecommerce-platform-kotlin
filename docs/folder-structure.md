# Project Folder Structure

This document describes the detailed folder and module layout of the `ecommerce-platform-kotlin` project.
charts
├── helm-cheatsheet.md
├── payment-consumers
│   ├── Chart.yaml
│   ├── templates
│   │   ├── _helpers.tpl
│   │   ├── deployment.yaml
│   │   ├── hpa.yaml
│   │   ├── service.yaml
│   │   └── servicemonitor.yaml
│   └── values.yaml
├── payment-platform-config
│   ├── Chart.yaml
│   └── templates
│       ├── configmap.yaml
│       └── secret.yaml
├── payment-service
│   ├── Chart.yaml
│   ├── templates
│   │   ├── _helpers.tpl
│   │   ├── configmap.yaml
│   │   ├── create-app-db-credentials-job.yaml
│   │   ├── deployment.yaml
│   │   ├── grant-app-db-privileges-job.yaml
│   │   ├── hpa.yaml
│   │   ├── ingress.yaml
│   │   ├── pvc.yaml
│   │   ├── service-monitor.yaml
│   │   └── service.yaml
│   └── values.yaml
└── step-by-step-env-management
common
├── pom.xml
└── src
├── main
│   └── kotlin
│       └── com
│           └── dogancaglar
│               └── common
│                   ├── dto
│                   │   └── PaymentOrderStatusUpdateRequest.kt
│                   ├── event
│                   │   ├── DomainEventEnvelopeFactory.kt
│                   │   ├── EventEnvelope.kt
│                   │   ├── EventMetadata.kt
│                   │   ├── PublicAggregateEvent.kt
│                   │   └── Topics.kt
│                   └── logging
│                       ├── GenericLogFields.kt
│                       └── LogContext.kt
└── test
└── kotlin
├── com
│   └── dogancaglar
│       └── common
│           └── event
│               └── DomainEventEnvelopeFactoryTest.kt
└── logging
└── LogContextTest.kt
docs
├── PaymentPllatform-Runbook.,md
├── ScalabilityMilestones.md
├── architecture.md
├── cheatsheet
│   ├── ELK.md
│   ├── connectivity.md
│   ├── docker-cli-cheatsheet.md
│   ├── kafka.md
│   ├── kubernetes-cheatsheet.md
│   ├── minikube-cheatsheet.md
│   └── nopan.md
├── folder-structure.md
├── how-to-start.md
├── payment-flow.md
└── retry-flow.md
filebeat
└── filebeat.yml
infra
├── endpoints.json
├── grafana
│   └── dashboards
├── helm-values
│   ├── elasticsearch-values-local.yaml
│   ├── filebeat-values-local.yaml
│   ├── ingress-values.yaml
│   ├── kafka-defaults.yaml
│   ├── kafka-exporter-values-local.yaml
│   ├── kafka-values-local.yaml
│   ├── keycloak-values-local.yaml
│   ├── kibana-values-local.yaml
│   ├── monitoring-stack-values-local.yaml
│   ├── my-postgres-defaults.yaml
│   ├── payment-consumers-values-local.yaml
│   ├── payment-db-values-local.yaml
│   ├── payment-platform-config-values-local.yaml
│   ├── payment-service-values-local.yaml
│   └── redis-values-local.yaml
├── scripts
│   ├── add-consumer-lag-metric.sh
│   ├── bootstrap-minikube-cluster.sh
│   ├── build-and-push-payment-consumers-docker-repo.sh
│   ├── build-and-push-payment-service-docker-repo.sh
│   ├── create-app-db-credentials-local.sh
│   ├── delete-all-local.sh
│   ├── delete-kafka-local.sh
│   ├── delete-minikube-cluster.sh
│   ├── delete-monitoring-stack.sh
│   ├── delete-payment-db-local.sh
│   ├── deploy-all-local.sh
│   ├── deploy-elasticsearch.sh
│   ├── deploy-filebeat.sh
│   ├── deploy-kafka-exporter-local.sh
│   ├── deploy-kafka-local.sh
│   ├── deploy-keycloak-local.sh
│   ├── deploy-kibana.sh
│   ├── deploy-monitoring-stack.sh
│   ├── deploy-observability-stack.sh
│   ├── deploy-payment-consumers-local.sh
│   ├── deploy-payment-db-local.sh
│   ├── deploy-payment-platform-config.sh
│   ├── deploy-payment-service-local.sh
│   ├── deploy-redis-local.sh
│   └── port-forwarding.sh
└── secrets
└── payment-platform-config-secrets-local.yaml
keycloak
├── access.token
├── get-token.sh
├── output
│   ├── keycloak-port-forward.log
│   └── secrets.txt
└── provision-keycloak.sh
load-tests
├── baseline-smoke-test.js
├── readme.md
└── startegy.md
mvnw  [error opening dir]
mvnw.cmd  [error opening dir]
order-service
└── pom.xml
payment-application
├── pom.xml
└── src
├── main
│   └── kotlin
│       └── com
│           └── dogancaglar
│               └── paymentservice
│                   ├── application
│                   │   ├── constants
│                   │   │   ├── IdNamespaces.kt
│                   │   │   └── PaymentLogFields.kt
│                   │   ├── model
│                   │   │   └── LedgerEntry.kt
│                   │   ├── service
│                   │   └── usecases
│                   │       ├── CreatePaymentService.kt
│                   │       ├── LedgerEntryFactory.kt
│                   │       ├── ProcessPaymentService.kt
│                   │       ├── RecordLedgerEntriesService.kt
│                   │       └── RequestLedgerRecordingService.kt
│                   └── ports
│                       ├── inbound
│                       │   ├── CreatePaymentUseCase.kt
│                       │   ├── ProcessPspResultUseCase.kt
│                       │   ├── RecordLedgerEntriesUseCase.kt
│                       │   └── RequestLedgerRecordingUseCase.kt
│                       └── outbound
│                           ├── EventPublisherPort.kt
│                           ├── IdGeneratorPort.kt
│                           ├── LedgerEntryPort.kt
│                           ├── OutboxEventPort.kt
│                           ├── PaymentGatewayPort.kt
│                           ├── PaymentOrderModificationPort.kt
│                           ├── PaymentOrderRepository.kt
│                           ├── PaymentOrderStatePort.kt
│                           ├── PaymentOrderStatusCheckRepository.kt
│                           ├── PaymentRepository.kt
│                           ├── PspResultCachePort.kt
│                           ├── RetryQueuePort.kt
│                           └── SerializationPort.kt
└── test
└── kotlin
├── com
│   └── dogancaglar
│       └── paymentservice
│           ├── application
│           │   ├── service
│           │   └── usecases
│           │       ├── CreatePaymentServiceTest.kt
│           │       ├── ProcessPaymentServiceTest.kt
│           │       ├── RecordLedgerEntriesServiceLedgerContentTest.kt
│           │       └── RequestLedgerRecordingServiceTest.kt
│           └── kafka
│               └── events
│                   ├── DomainEnvelopeFactoryTest.kt
│                   └── DummyEventMetadataTest.kt
└── om
└── dogancaglar
└── paymentservice
└── application
payment-consumers
├── Dockerfile
├── Dockerfile.bak
├── pom.xml
└── src
├── main
│   ├── kotlin
│   │   └── com
│   │       └── dogancaglar
│   │           ├── PaymentConsumersApplication.kt
│   │           └── paymentservice
│   │               ├── PaymentConsumersAutoConfig.kt
│   │               ├── adapter
│   │               │   └── outbound
│   │               │       ├── persistance
│   │               │       │   └── mybatis
│   │               │       └── psp
│   │               │           ├── NetworkSimulator.kt
│   │               │           ├── PSPResponse.kt
│   │               │           ├── PaymentGatewayAdapter.kt
│   │               │           └── PspSimulationProperties.kt
│   │               ├── application
│   │               │   └── constants
│   │               ├── consumers
│   │               │   ├── DynamicKafkaConsumersProperties.kt
│   │               │   └── KafkaTypedConsumerFactoryConfig.kt
│   │               ├── port
│   │               │   └── inbound
│   │               │       └── consumers
│   │               │           ├── LedgerRecordingConsumer.kt
│   │               │           ├── LedgerRecordingRequestDispatcher.kt
│   │               │           ├── PaymentOrderEnqueuer.kt
│   │               │           ├── PaymentOrderPspCallExecutor.kt
│   │               │           ├── PaymentOrderPspResultApplier.kt
│   │               │           ├── ScheduledPaymentStatusCheckExecutor.kt
│   │               │           ├── base
│   │               │           │   ├── AbstractKafkaConsumer.kt
│   │               │           │   ├── BaseBatchKafkaConsumer.kt
│   │               │           │   └── BaseSingleKafkaConsumer.kt
│   │               │           └── config
│   │               │               ├── ConsumerThreadPoolConfig.kt
│   │               │               └── PaymentConsumerConfig.kt
│   │               ├── ports
│   │               │   └── outbound
│   │               └── service
│   │                   ├── LedgerEntryTxAdapter.kt
│   │                   ├── PaymentOrderModificationTxAdapter.kt
│   │                   └── RetryDispatcherScheduler.kt
│   └── resources
│       ├── META-INF
│       │   └── spring
│       │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       ├── application-docker.yml
│       ├── application-local.yml
│       ├── application.yml
│       ├── logback-spring.xml
│       └── mapper
└── test
├── kotlin
│   └── com
│       └── dogancaglar
│           └── paymentservice
│               ├── adapter
│               │   └── outbound
│               │       └── psp
│               │           ├── NetworkSimulatorTest.kt
│               │           └── PaymentGatewayAdapterTest.kt
│               ├── port
│               │   └── inbound
│               │       └── consumers
│               │           ├── LedgerRecordingConsumerTest.kt
│               │           ├── LedgerRecordingRequestDispatcherTest.kt
│               │           ├── PaymentOrderEnqueuerTest.kt
│               │           ├── PaymentOrderPspCallExecutorTest.kt
│               │           └── PaymentOrderPspResultApplierTest.kt
│               └── service
│                   └── LedgerEntryTxAdapterTest.kt
└── resources
payment-db
└── postgresql.conf
payment-domain
├── pom.xml
└── src
├── main
│   └── kotlin
│       └── com
│           └── dogancaglar
│               └── paymentservice
│                   ├── domain
│                   │   ├── commands
│                   │   │   ├── CreatePaymentCommand.kt
│                   │   │   ├── LedgerCommand.kt
│                   │   │   └── LedgerRecordingCommand.kt
│                   │   ├── event
│                   │   │   ├── EventMetadatas.kt
│                   │   │   ├── LedgerEntriesRecorded.kt
│                   │   │   ├── LedgerEvent.kt
│                   │   │   ├── OutboxEvent.kt
│                   │   │   ├── PaymentEvent.kt
│                   │   │   ├── PaymentOrderCreated.kt
│                   │   │   ├── PaymentOrderEvent.kt
│                   │   │   ├── PaymentOrderFailed.kt
│                   │   │   ├── PaymentOrderPspCallRequested.kt
│                   │   │   ├── PaymentOrderPspResultUpdated.kt
│                   │   │   ├── PaymentOrderStatusCheckRequested.kt
│                   │   │   └── PaymentOrderSucceeded.kt
│                   │   ├── exception
│                   │   │   ├── PaymentDomainExceptions.kt
│                   │   │   ├── PaymentValidationException.kt
│                   │   │   └── PspUnavailableException.kt
│                   │   ├── model
│                   │   │   ├── Amount.kt
│                   │   │   ├── Payment.kt
│                   │   │   ├── PaymentOrder.kt
│                   │   │   ├── PaymentOrderStatus.kt
│                   │   │   ├── PaymentOrderStatusCheck.kt
│                   │   │   ├── PaymentStatus.kt
│                   │   │   ├── RetryItem.kt
│                   │   │   ├── ScheduledPaymentOrderStatusRequest.kt
│                   │   │   ├── ledger
│                   │   │   │   ├── Account.kt
│                   │   │   │   ├── JournalEntry.kt
│                   │   │   │   ├── JournalType.kt
│                   │   │   │   └── Posting.kt
│                   │   │   └── vo
│                   │   │       ├── BuyerId.kt
│                   │   │       ├── OrderId.kt
│                   │   │       ├── PaymentId.kt
│                   │   │       ├── PaymentLine.kt
│                   │   │       ├── PaymentOrderId.kt
│                   │   │       └── SellerId.kt
│                   │   └── util
│                   │       ├── PSPStatusMapper.kt
│                   │       ├── PaymentFactory.kt
│                   │       ├── PaymentOrderEventMapper.kt
│                   │       └── PaymentOrderFactory.kt
│                   └── ports
│                       └── outbound
└── test
└── kotlin
└── com
└── dogancaglar
└── paymentservice
└── domain
├── commands
│   └── CreatePaymentCommandTest.kt
├── event
│   └── OutboxEventTest.kt
├── model
│   ├── AmountTest.kt
│   ├── PaymentOrderTest.kt
│   ├── PaymentTest.kt
│   └── ledger
│       └── JournalEntryTest.kt
└── util
├── PSPStatusMapperTest.kt
├── PaymentFactoryTest.kt
├── PaymentOrderEventMapperTest.kt
└── PaymentOrderFactoryTest.kt
payment-infrastructure
├── pom.xml
└── src
├── main
│   ├── kotlin
│   │   └── com
│   │       └── dogancaglar
│   │           └── paymentservice
│   │               ├── adapter
│   │               │   └── outbound
│   │               │       ├── kafka
│   │               │       │   └── PaymentEventPublisher.kt
│   │               │       ├── ledger
│   │               │       ├── persistance
│   │               │       │   ├── OutboxBufferAdapter.kt
│   │               │       │   ├── PaymentOrderOutboundAdapter.kt
│   │               │       │   ├── PaymentOrderStatusCheckAdapter.kt
│   │               │       │   ├── PaymentOutboundAdapter.kt
│   │               │       │   ├── entity
│   │               │       │   │   ├── JournalEntryEntity.kt
│   │               │       │   │   ├── LedgerEntryEntity.kt
│   │               │       │   │   ├── OutboxEventEntity.kt
│   │               │       │   │   ├── PaymentEntity.kt
│   │               │       │   │   ├── PaymentOrderEntity.kt
│   │               │       │   │   ├── PaymentOrderStatusCheckEntity.kt
│   │               │       │   │   ├── PostingEntity.kt
│   │               │       │   │   └── accounting
│   │               │       │   └── mybatis
│   │               │       │       ├── LedgerMapper.kt
│   │               │       │       ├── LedgerPersistenceMapper.kt
│   │               │       │       ├── OutboxEventEntityMapper.kt
│   │               │       │       ├── OutboxEventMapper.kt
│   │               │       │       ├── PaymentEntityMapper.kt
│   │               │       │       ├── PaymentMapper.kt
│   │               │       │       ├── PaymentOrderEntityMapper.kt
│   │               │       │       ├── PaymentOrderMapper.kt
│   │               │       │       ├── PaymentOrderStatusCheckEntityMapper.kt
│   │               │       │       ├── PaymentOrderStatusCheckMapper.kt
│   │               │       │       └── typehandler
│   │               │       │           ├── PaymentOrderStatusTypeHandler.kt
│   │               │       │           └── UUIDTypeHandler.kt
│   │               │       ├── redis
│   │               │       │   ├── PaymentRetryQueueAdapter.kt
│   │               │       │   ├── PaymentRetryRedisCache.kt
│   │               │       │   ├── PspResultRedisCacheAdapter.kt
│   │               │       │   └── RedisIdGeneratorPortAdapter.kt
│   │               │       └── serialization
│   │               │           └── JacksonSerializationAdapter.kt
│   │               ├── application
│   │               │   └── constants
│   │               ├── config
│   │               │   └── kafka
│   │               │       ├── EventEnvelopeKafkaDeserializer.kt
│   │               │       ├── EventEnvelopeKafkaSerializer.kt
│   │               │       ├── JacksonUtil.kt
│   │               │       ├── KafkaProducerConfig.kt
│   │               │       ├── KafkaTopicsProperties.kt
│   │               │       └── KafkaTxExecutor.kt
│   │               ├── infrastructure
│   │               │   └── PaymentInfrastructureAutoConfig.kt
│   │               ├── metrics
│   │               │   ├── JobMetrics.kt
│   │               │   ├── KafkaMetrics.kt
│   │               │   ├── MetricHelper.kt
│   │               │   ├── MetricNames.kt
│   │               │   └── RedisMetrics.kt
│   │               ├── ports
│   │               │   └── outbound
│   │               └── redis
│   │                   └── RedisConfig.kt
│   └── resources
│       ├── META-INF
│       │   └── spring
│       │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       ├── db
│       │   ├── changelog
│       │   │   ├── 2025
│       │   │   │   └── 10
│       │   │   │       ├── 23
│       │   │   │       └── 24
│       │   │   ├── 2025-09-18-reset-autovacuum-payment-orders.xml
│       │   │   ├── 2025-10-27-ledger-entry-tables.xml
│       │   │   ├── 20250518-create-scheduled-payment-order-status-request.xml
│       │   │   ├── 20250526-remove-paymentorderid-payment.xml
│       │   │   ├── 20250526-remove-public-paymentorderid-payment.xml
│       │   │   ├── 20250526-remove-sellerid-payment.xml
│       │   │   ├── 20250601-create-payment-order-status-checks.xml
│       │   │   ├── 20250615-add-pk-on-payment-id.xml
│       │   │   ├── 20251014-change-amount-value-to-bigint.xml
│       │   │   ├── changelog.master.xml
│       │   │   ├── payment-changelog.xml
│       │   │   ├── payment-order-changelog.xml
│       │   │   └── payment-outbox-changelog.xml
│       │   └── migration
│       ├── mapper
│       │   ├── LedgerMapper.xml
│       │   ├── OutboxEventMapper.xml
│       │   ├── PaymentMapper.xml
│       │   ├── PaymentOrderMapper.xml
│       │   └── PaymentOrderStatusCheckMapper.xml
│       └── sql
│           ├── checklist.txt
│           ├── outbox
│           │   ├── create-missing-partitions.sql
│           │   ├── outbox-create-table-partition-index.sql
│           │   ├── prunte-old-partitions.sql
│           │   └── remove-partitions.sql
│           ├── partitioning.sql
│           └── performance-tuning.sql
└── test
├── kotlin
│   └── com
│       └── dogancaglar
│           └── paymentservice
│               ├── InfraTestBoot.kt
│               ├── adapter
│               │   └── outbound
│               │       ├── ledger
│               │       ├── persistance
│               │       │   ├── OutboxBufferAdapterTest.kt
│               │       │   ├── PaymentOrderOutboundAdapterTest.kt
│               │       │   ├── PaymentOrderStatusCheckAdapterEdgeCasesTest.kt
│               │       │   ├── PaymentOrderStatusCheckAdapterMappingTest.kt
│               │       │   ├── PaymentOrderStatusCheckAdapterTest.kt
│               │       │   ├── PaymentOutboundAdapterIntegrationTest.kt
│               │       │   ├── PaymentOutboundAdapterTest.kt
│               │       │   └── mybatis
│               │       │       └── EntityMapperTest.kt
│               │       ├── redis
│               │       │   ├── PaymentRetryQueueAdapterIntegrationTest.kt
│               │       │   ├── PaymentRetryQueueAdapterTest.kt
│               │       │   ├── PaymentRetryRedisCacheIntegrationTest.kt
│               │       │   ├── PaymentRetryRedisCacheTest.kt
│               │       │   ├── PspResultRedisCacheAdapterIntegrationTest.kt
│               │       │   ├── PspResultRedisCacheAdapterTest.kt
│               │       │   ├── RedisIdGeneratorPortAdapterIntegrationTest.kt
│               │       │   └── RedisIdGeneratorPortAdapterTest.kt
│               │       └── serialization
│               │           └── JacksonSerializationAdapterTest.kt
│               ├── integration
│               └── mybatis
│                   ├── LedgerMapperIntegrationTest.kt
│                   ├── OutboxEventMapperIntegrationTest.kt
│                   └── PaymentOrderMapperIntegrationTest.kt
└── resources
├── application-integration.yaml
├── application-test.yaml
├── application.yaml
└── schema-test.sql
payment-service
├── Dockerfile
├── pom.xml
└── src
├── main
│   ├── kotlin
│   │   └── com
│   │       └── dogancaglar
│   │           └── paymentservice
│   │               ├── PaymentServiceApplication.kt
│   │               ├── adapter
│   │               │   └── inbound
│   │               │       └── rest
│   │               │           ├── PaymentController.kt
│   │               │           ├── PaymentControllerWebExceptionHandler.kt
│   │               │           ├── PaymentService.kt
│   │               │           ├── SecurityConfig.kt
│   │               │           ├── TraceFilter.kt
│   │               │           ├── dto
│   │               │           │   ├── AmountDto.kt
│   │               │           │   ├── PaymentOrderRequestDTO.kt
│   │               │           │   ├── PaymentOrderResponseDTO.kt
│   │               │           │   ├── PaymentRequestDTO.kt
│   │               │           │   └── PaymentResponseDTO.kt
│   │               │           └── mapper
│   │               │               ├── AmountMapper.kt
│   │               │               └── PaymentRequestMapper.kt
│   │               └── application
│   │                   ├── IdResyncStartup.kt
│   │                   ├── config
│   │                   │   ├── DBWriterTxManager.kt
│   │                   │   ├── KafkaTopicsConfig.kt
│   │                   │   ├── MultiDataSourceConfig.kt
│   │                   │   ├── MyBatisFactoriesConfig.kt
│   │                   │   ├── PaymentServiceConfig.kt
│   │                   │   └── ThreadPoolConfig.kt
│   │                   └── maintenance
│   │                       ├── OutboxDispatcherJob.kt
│   │                       ├── OutboxJobMyBatisAdapter.kt
│   │                       └── OutboxPartitionCreator.kt
│   └── resources
│       ├── application-docker.yml
│       ├── application-gke.yml
│       ├── application-kubernetes.bak
│       ├── application-local.yml
│       ├── application-local.yml.bak
│       ├── application.yml
│       ├── logback-spring.xml
│       └── static
│           └── favicon.ico
└── test
├── kotlin
│   └── com
│       └── dogancaglar
│           └── paymentservice
│               ├── adapter
│               │   └── inbound
│               │       └── rest
│               │           ├── PaymentControllerTest.kt
│               │           ├── PaymentServiceTest.kt
│               │           └── mapper
│               │               └── PaymentRequestMapperTest.kt
│               ├── application
│               │   └── maintenance
│               │       └── OutboxDispatcherJobTest.kt
│               ├── event
│               │   └── DomainEventFactoryTest.kt
│               └── security
└── resources
└── application.yaml
pom.xml  [error opening dir]
prometheus
├── grafana-provisioning
│   ├── dashboards
│   │   ├── dashboards.yml
│   │   └── grafana-dashboard.json
│   └── datasources.yml
├── grafana-sample-dashboards
│   ├── Payment Dashboard Jun 17-1750462307801.json
│   └── payment-dashboard-10082025.json
├── grafana.ini
└── prometheus.yml
shipment-service
└── pom.xml
wallet-service
└── pom.xml
```

---

## Explanation

- **common**  
  Contains shared models, event envelopes, logging utilities, and ID generation logic used by all modules.

- **payment-service**  
  The heart of the project currently. Follows Hexagonal Architecture:
    - `adapter` layer contains Kafka, Redis, database persistence, outbox pattern implementations.
    - `application` layer has services, events, mappers, and helpers.
    - `domain` layer holds business rules, domain exceptions, model entities, and ports/interfaces.
    - `psp` simulates the payment service provider with controlled failures and delays.
    - `web` exposes REST APIs and DTOs.
    - Resources include DB migration scripts and logback config.

- **order-service**, **wallet-service**, **shipment-service**  
  Future planned modules for other domains, each will be a standalone module.

- **docs**  
  Contains detailed documentation for architecture, design, and project layout.

- **filebeat**  
  Config for shipping logs to Elasticsearch for observability.

- **docker-compose.yml**  
  Local development orchestration of all services and dependencies.

---

*Maintained by Doğan Çağlar.*