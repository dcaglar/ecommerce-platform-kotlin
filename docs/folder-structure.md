# Project Folder Structure

This document describes the detailed folder and module layout of the `ecommerce-platform-kotlin` project.

```
ecommerce-platform-kotlin
├── CONFIG                      # Project-wide configuration files (if any)
├── README.md                   # Main project overview and quickstart guide
├── common                      # Shared utilities, models, and abstractions
│   ├── pom.xml                 # Module POM for common
│   └── src
│       ├── main
│       │   └── kotlin
│       │       └── com
│       │           └── dogancaglar
│       │               └── common
│       │                   ├── event              # Event envelope, metadata, and factories
│       │                   ├── id                 # ID generation utilities
│       │                   └── logging            # MDC context helpers, log fields
│       └── test
│           └── kotlin
│               └── logging
│                   └── LogContextTest.kt
├── docker-compose.yml          # Docker Compose setup for local dev environment
├── docs                       # Documentation files
│   ├── architecture.md         # Architecture overview and deployment plans
│   └── folder-structure.md     # This document
├── filebeat                   # Filebeat configuration for log shipping
│   └── filebeat.yml
├── mvnw                       # Maven wrapper script (Linux/macOS)
├── mvnw.cmd                   # Maven wrapper script (Windows)
├── order-service              # Planned module for order management
│   └── pom.xml
├── payment-service            # Core payment domain service
│   ├── README.md              # Payment service specific documentation
│   ├── payment-service.iml    # IDE module file (IntelliJ)
│   ├── pom.xml                # Payment module POM
│   └── src
│       ├── main
│       │   ├── kotlin
│       │   │   └── com
│       │   │       └── dogancaglar
│       │   │           └── paymentservice
│       │   │               ├── adapter             # Ports & Adapters layer
│       │   │               │   ├── delayqueue      # Delayed retry queues, scheduling jobs
│       │   │               │   ├── kafka           # Kafka consumers and producers
│       │   │               │   ├── outbox          # Outbox pattern dispatching
│       │   │               │   ├── persistence     # JPA entities, repositories, mappers
│       │   │               │   └── redis           # Redis adapters for retry, ID generation
│       │   │               ├── application         # Application services, events, mappers, helpers
│       │   │               ├── config              # Spring Boot configuration (Kafka, Redis, Security)
│       │   │               ├── domain              # Domain model, exceptions, factories, ports
│       │   │               ├── psp                 # Mock Payment Service Provider client and simulation
│       │   │               └── web                 # REST controllers and DTOs
│       │   └── resources
│       │       ├── application.yml
│       │       ├── db
│       │       │   └── changelog                  # Liquibase database migration files
│       │       ├── logback-spring.xml             # Logback JSON + MDC config
│       │       └── redis
│       │           └── redis.conf                  # Redis config file
│       └── test                                   # Unit and integration tests
│           ├── kotlin
│           │   └── com
│           │       └── dogancaglar
│           │           └── paymentservice
│           │               ├── adapter
│           │               ├── domain
│           │               ├── e2e                 # End-to-end tests
│           │               ├── id
│           │               └── web
│           └── resources
│               └── application.yml
├── shipment-service          # Planned module for shipment coordination
│   └── pom.xml
└── wallet-service            # Planned module for wallet/balance management
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