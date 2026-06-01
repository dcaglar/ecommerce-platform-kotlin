i# AI Agent Instructions: Kafka and Database Multi-Module Refactoring



## Purpose

This document provides precise structural rules, boundary constraints, and code templates to refactor our monolithic Maven application into a clean, multi-module architecture.



The goal is to eliminate duplicate configurations across the **Web Application** (`web-app`) and the **Scheduled Job** (`scheduled-job`) by extracting infrastructure boilerplate into shared modules, while enforcing strict domain isolation.



---



## Module Hierarchy Target



[ Corporate Parent POM ] (Remote Dependency)

▲

│ (Inherits via )

│

[ payment-service] (Executable Application)
 [ payment-edge-worker jon ]   (Packaging: jar - Executable Application)


▲

├── [ common-database ] (Packaging: jar - Infrastructure Only) -new

├── [ common-kafka ]    (Packaging: jar - Infrastructure Only) -new

├── [payment-consuemrs ]         (Packaging: jar - Executable Application)

└── [Relay job ]   (Packaging: jar - Executable Application)





---



## Step 1: Root POM Configuration (`pom.xml`)

**Action:** Update or create the root `pom.xml` to link all modules. Ensure the local root points to our corporate parent.



```xml

<project xmlns="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0)">

    <modelVersion>4.0.0</modelVersion>

    

    <parent>

        <groupId>com.yourcompany.architecture</groupId>

        <artifactId>corporate-parent-pom</artifactId>

        <version>2026.3.1</version>

        <relativePath/>

    </parent>



    <groupId>com.yourcompany.payments</groupId>

    <artifactId>payment-service-root</artifactId>

    <version>1.0.0-SNAPSHOT</version>

    <packaging>pom</packaging>



    <modules>

        <module>common-database</module>

        <module>common-kafka</module>

        <module>web-app</module>

        <module>scheduled-job</module>

    </modules>

</project>

Step 2: Database Layer Refactoring

1. common-database Boundary

Allowed: HikariCP configuration, DataSource, SqlSessionFactory, PlatformTransactionManager, global interceptors.

Prohibited: MyBatis @Mapper interfaces, XML query maps, Flyway/Liquibase migration SQL scripts, domain Entities/DTOs.

Execute Code Creation: SharedDatabaseAutoConfiguration.java

Create this file in common-database under src/main/java/com/yourcompany/payments/database/:



Java



package com.yourcompany.payments.database;import com.zaxxer.hikari.HikariConfig;import com.zaxxer.hikari.HikariDataSource;import org.apache.ibatis.session.SqlSessionFactory;import org.mybatis.spring.SqlSessionFactoryBean;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.context.annotation.Bean;import org.springframework.context.annotation.Configuration;import org.springframework.core.io.support.PathMatchingResourcePatternResolver;import javax.sql.DataSource;@Configuration@ConfigurationProperties(prefix = "app.datasource.hikari")public class SharedDatabaseAutoConfiguration {



    private int maximumPoolSize = 10;

    private long connectionTimeout = 30000;

    private long idleTimeout = 600000;



    @Bean

    public DataSource dataSource() {

        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(System.getenv("DB_URL"));

        config.setUsername(System.getenv("DB_USERNAME"));

        config.setPassword(System.getenv("DB_PASSWORD"));

        

        config.setMaximumPoolSize(this.maximumPoolSize);

        config.setConnectionTimeout(this.connectionTimeout);

        config.setIdleTimeout(this.idleTimeout);

        return new HikariDataSource(config);

    }



    @Bean

    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {

        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();

        sessionFactory.setDataSource(dataSource);

        sessionFactory.setMapperLocations(

            new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")

        );

        return sessionFactory.getObject();

    }



    // Agent: Generate standard getters and setters for the properties below

    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }

    public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }

    public void setIdleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; }

}

2. Application Domain Boundary (Web & Job)

Action: Move application-specific mappers and Flyway migrations into their respective application modules.

Action: Restrict MyBatis scanning using precise package paths in the application main classes.

Web App Target Entry Point:

Java



@SpringBootApplication@MapperScan("com.yourcompany.payments.webapp.mappers") // Scans ONLY Web table mapperspublic class WebApplication {

    public static void main(String[] args) {

        SpringApplication.run(WebApplication.class, args);

    }

}

Scheduled Job Target Entry Point:

Java



@SpringBootApplication@MapperScan("com.yourcompany.payments.job.mappers") // Scans ONLY Job table mapperspublic class ScheduledJobApplication {

    public static void main(String[] args) {

        SpringApplication.run(ScheduledJobApplication.class, args);

    }

}

Step 3: Kafka Layer Refactoring

1. common-kafka Boundary

Allowed: SSL/TLS properties config, SASL authentication, ProducerFactory, ConsumerFactory, global DefaultErrorHandler (Dead Letter Topic mechanics), tracing interceptors.

Prohibited: @KafkaListener classes, NewTopic beans, Event DTOs/Schemas.

Execute Code Creation: SharedKafkaAutoConfiguration.java

Create this file in common-kafka under src/main/java/com/yourcompany/payments/kafka/:



Java



package com.yourcompany.payments.kafka;import org.apache.kafka.clients.consumer.ConsumerConfig;import org.apache.kafka.clients.producer.ProducerConfig;import org.springframework.boot.autoconfigure.AutoConfiguration;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.context.annotation.Bean;import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;import org.springframework.kafka.core.*;import java.util.HashMap;import java.util.Map;@AutoConfiguration@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)public class SharedKafkaAutoConfiguration {



    @Bean

    public ProducerFactory<String, Object> producerFactory() {

        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("KAFKA_BOOTSTRAP_SERVERS"));

        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonSerializer");

        // Agent: Append global SSL/SASL security configurations here

        return new DefaultKafkaProducerFactory<>(config);

    }



    @Bean

    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {

        return new KafkaTemplate<>(producerFactory);

    }



    @Bean

    public ConsumerFactory<String, Object> consumerFactory() {

        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("KAFKA_BOOTSTRAP_SERVERS"));

        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");

        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonDeserializer");

        return new DefaultKafkaConsumerFactory<>(config);

    }



    @Bean

    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(

            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        return factory;

    }

}

2. Application Domain Boundary (Web & Job)

Action: Keep @KafkaListener definitions and domain schemas (e.g., OrderPlacedEvent) inside the runtime modules.

Action: If a module does not utilize Kafka consumers (e.g., short-lived job), disable Kafka scanning explicitly or omit the dependency entirely.

Step 4: Runtime Configuration Configuration Tuning

Action: Adjust application configuration profiles to optimize execution pools based on deployment workloads.



web-app/src/main/resources/application.yml

YAML



app:

  kafka:

    enabled: true

  datasource:

    hikari:

      maximum-pool-size: 50      # Optimized for parallel API requests

      connection-timeout: 5000   # Fast-fail for synchronous web traffic

scheduled-job/src/main/resources/application.yml

YAML



app:

  kafka:

    enabled: false               # Turn off baseline Kafka consumers if only acting as a cron processing tool

  datasource:

    hikari:

      maximum-pool-size: 2       # Prevents batch execution from draining DB connections

      connection-timeout: 120000 # 2 minutes buffer to wait for long running locks to clear

Agent Verification Checklist

Before declaring the task complete, verify the following:



Run mvn clean package at the root level. Confirm that two executable fat JARs are built inside web-app/target/ and scheduled-job/target/.

Inspect the content of common-database and common-kafka. Confirm no domain specific entity files, .sql schema migration scripts, or @Mapper files exist inside them.

Validate that neither runtime application configuration conflicts with each other when run concurrently in isolated spaces.








RECOMENDTATIONS

This is a very common requirement in high-scale systems. Even though both applications connect to the exact same database engine, a long-running HTTP Web Application and a short-lived, heavy-compute Scheduled Job have completely different runtime profiles, meaning they require vastly different pool configurations.

The Web App needs a large pool size to handle concurrent incoming web requests, but short connection timeouts so web threads don't hang indefinitely if the database slows down.

The Scheduled Job usually runs single-threaded or with minor batch parallelism, requiring a small pool size (sometimes just 1 to 3 connections), but very long connection/socket timeouts so massive batch updates or heavy read queries don't get terminated halfway through.

Here is the best practice for building a shared infrastructure module that handles application-specific overrides seamlessly.

The Solution: Externalized Configuration via Spring Boot Properties
Your common-database module should configure the Hikari pool programmatically using sensible defaults, but bind itself to Spring's environment properties. This allows each individual application's application.yml file to override the values at runtime without touching a single line of Java code.

1. The Code in common-database
Instead of hardcoding values like maximum-pool-size = 10, you inject the values directly using Spring's @ConfigurationProperties.

Java
@Configuration
@ConfigurationProperties(prefix = "app.datasource.hikari")
public class SharedDatabaseAutoConfiguration {

    // Define sensible global defaults directly in Java
    private int maximumPoolSize = 10; 
    private long connectionTimeout = 30000; // 30 seconds
    private long idleTimeout = 600000; // 10 minutes

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // These can be passed universally from K8s secrets/configmaps
        config.setJdbcUrl(System.getenv("DB_URL"));
        config.setUsername(System.getenv("DB_USERNAME"));
        config.setPassword(System.getenv("DB_PASSWORD"));

        // Bind the configurable pool settings
        config.setMaximumPoolSize(this.maximumPoolSize);
        config.setConnectionTimeout(this.connectionTimeout);
        config.setIdleTimeout(this.idleTimeout);

        return new HikariDataSource(config);
    }

    // Standard getters and setters for Spring to bind configuration properties
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
    public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public void setIdleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; }
}
2. Overriding the Settings inside the App Modules
Now that the common module is built to look at app.datasource.hikari.* for overrides, you tune your infrastructure entirely within your applications' local resource files.

In the web-app's application.yml
The web app needs a wider pool to support concurrent traffic.

YAML
app:
  datasource:
    hikari:
      maximum-pool-size: 50       # High pool size for high concurrent web traffic
      connection-timeout: 5000    # 5 seconds fast-fail timeout so web users aren't waiting
      idle-timeout: 300000
In the scheduled-job's application.yml
The batch job needs very few connections, but they need to be highly resilient against long-running processing cycles.

YAML
app:
  datasource:
    hikari:
      maximum-pool-size: 2        # Very small pool; prevents draining the DB pool during a batch run
      connection-timeout: 120000  # 2 minutes; allows the job to wait patiently for a connection
      idle-timeout: 60000         # Aggressively drop idle connections when job finishes
Summary of the Engineering Reasoning
By setting up your shared architecture this way, you satisfy both requirements perfectly:

You eliminate code duplication: The Java logic that wires up the HikariDataSource, handles driver class instantiation, and manages authentication secrets lives in exactly one place (common-database).

You maintain infrastructure isolation: At runtime in Kubernetes, when the Web App container spins up, it configures its own internal Hikari engine to allocate 50 connections. When the CronJob container spins up, its internal Hikari instance only opens 2 connections. They operate completely independently based on their tailored runtime profiles.



edge csaes in the case of multiple data sources

Scenario A: Each module connects to exactly ONE database (But they are all different)
This is the most common microservices setup. The web app only talks to DB #1, the job only talks to DB #2, and service #3 only talks to DB #3.

In this case, common-database remains 100% generic. It only defines one single abstract blueprint for a DataSource. It does not know or care that there are 3 databases in the world.

What is configured in common-database?
A Single Blueprint Bean: One @Bean DataSource factory method that reads from generic variables (like ${spring.datasource.url}).

The MyBatis Engine Factory: One SqlSessionFactory bean configuration.

How do they connect to 3 different databases?
You pass different environment variables to each container at runtime in Kubernetes, or use different properties in their local application.yml files.

web-app configures: spring.datasource.url=jdbc:mysql://server-1/orders_db

scheduled-job configures: spring.datasource.url=jdbc:postgresql://server-2/reconciliation_db

Spring Boot automatically injects the local application's properties into the shared common-database blueprint at startup.

Scenario B: One module needs to connect to MULTIPLE databases at the same time
If your scheduled-job needs to read data from the Web App's orders_db AND write the results into its own reconciliation_db simultaneously, a single generic blueprint isn't enough.

In this scenario, common-database must be configured to support Named Multi-DataSources.

What is configured in common-database in this case?
Instead of creating one generic DataSource, you configure specific, named configuration properties and routing mechanisms inside the shared module.

1. Define Unique Property Prefixes
You define explicit prefixes for each database configuration inside the shared module's Java code:

Java
// Inside common-database module

@Configuration
public class MultiDatabaseAutoConfiguration {

    // 1. Core Orders Database Setup
    @Bean
    @ConfigurationProperties(prefix = "app.datasource.orders")
    public DataSourceProperties ordersDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource ordersDataSource() {
        return ordersDataSourceProperties().initializeDataSourceBuilder().build();
    }

    // 2. Reconciliation Database Setup
    @Bean
    @ConfigurationProperties(prefix = "app.datasource.recon")
    public DataSourceProperties reconDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource reconDataSource() {
        return reconDataSourceProperties().initializeDataSourceBuilder().build();
    }
}
2. Configure Separate MyBatis Session Factories
Because MyBatis can only bind to one database pool per factory, common-database will set up named factories:

ordersSqlSessionFactory (pointing to ordersDataSource)

reconSqlSessionFactory (pointing to reconDataSource)

How the Application Module Uses It
If the scheduled-job needs both databases, its local application.yml will supply credentials for both prefixes:

YAML
app:
  datasource:
    orders:
      url: jdbc:postgresql://server-1/orders_db
      username: web_reader
    recon:
      url: jdbc:postgresql://server-2/reconciliation_db
      username: job_owner
Then, using @MapperScan, you map your Java interfaces to the correct database engine:

Scan com.job.mappers.orders using the ordersSqlSessionFactory.

Scan com.job.mappers.recon using the reconSqlSessionFactory.

Summary Checklist for common-database
If you have 3 databases, your common-database module will contain:

The Shared Library Classpath Drivers: Ensure database drivers for your systems (e.g., PostgreSQL, MySQL, Oracle) are listed as dependencies in common-database's pom.xml so they are available to all modules.

Conditional Configurations: Use @ConditionalOnProperty so that a module only activates the specific database connections it actually needs at runtime, keeping its memory footprint clean.






edge csaes in the case of one module is consume otyher one is producer, and some other rules

The Consumer (the web-app) should be responsible for declaring the NewTopic beans. Here is the systematic reasoning, the explicit rules, and how to structure it without breaking your multi-module design.

The Systematic Principle: "The Consumer Owns the Contract"
In event-driven architectures, the consumer dictates the ecosystem. If a producer fires a message into a topic that does not exist, Kafka can handle automatic topic creation (if enabled on the broker), or the producer will retry.

However, if a consumer starts up and attempts to subscribe to a non-existent topic, the consumer group initialization can stall, throw continuous warning loops, or fail to receive data if the broker doesn't auto-create topics on read.

Why the Producer (scheduled-job) should NOT declare the topic:
Short Lifecycle: Your scheduled job spins up, runs for a few minutes, and terminates. It should not be burdened with cluster administration task overhead during its rapid execution window.

Permissions (ACLs): In a secure corporate environment, a batch job might only have Write access to a topic, while cluster Create or Alter permissions are restricted.

The Architectural Blueprint
[ common-kafka ]
  └── Provides the technical infrastructure (Factories, Serializers)
          ▲
          ├── [ web-app ] (The Consumer)
          │     ├── Owns: @KafkaListener
          │     └── Owns: NewTopic Bean (Topic Provisioning) ◄── SYSTEMATIC RULE
          │
          └── [ scheduled-job ] (The Producer)
                └── Owns: KafkaTemplate.send()
Exact Implementation Guidelines
To make this a repeatable pattern for your agent or team, follow this strict split of code between your modules.

1. What goes into common-kafka?
The shared module should provide an easy way to create topics but must not instantiate any actual topics itself. It provides the tool, not the instance.

Java
// Inside common-kafka (Shared Module)
@Configuration
public class SharedKafkaAdminConfiguration {

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("KAFKA_BOOTSTRAP_SERVERS"));
        // Add SASL/SSL credentials here if needed for cluster admin tasks
        return new KafkaAdmin(configs);
    }
}
2. What goes into the web-app (The Consumer)?
The application that listens to the data defines the configuration infrastructure for the topic's size and partition landscape.

Java
// Inside web-app (Application Module)
@Configuration
public class WebAppTopicDefinitions {

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name("payment-orders-v1")
                .partitions(3)                 // Dictates consumer scaling layout
                .replicas(3)                   // High availability settings
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .build();
    }
}
3. What goes into the scheduled-job (The Producer)?
The producer contains zero configuration logic regarding the topic infrastructure. It treats the topic simply as a target string destination.

Java
// Inside scheduled-job (Application Module)
@Component
public class BatchEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BatchEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishSchedules(List<PaymentBatch> batches) {
        for (PaymentBatch batch : batches) {
            // The topic name is a string, completely decoupled from topic creation logic
            kafkaTemplate.send("payment-orders-v1", batch.getId(), batch);
        }
    }
}
Summary of the Systematic Rules
Infrastructure Classpath Rule: Put KafkaAdmin and common security configuration inside common-kafka.

Topic Creation Ownership Rule: Place all NewTopic beans exclusively inside the application module containing the corresponding @KafkaListener.

Environment Isolation Rule: Always match your topic partition sizes (.partitions(X)) to the maximum expected horizontal scaling capacity of your web-app deployment pods in Kubernetes.



