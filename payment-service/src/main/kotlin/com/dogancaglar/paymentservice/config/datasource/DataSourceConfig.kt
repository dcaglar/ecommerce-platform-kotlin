package com.dogancaglar.paymentservice.config.datasource

import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import io.micrometer.core.instrument.MeterRegistry
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

/**
 * Manual dual‑datasource configuration:
 *   • master – write DB, Liquibase target
 *   • replica – read‑only DB
 */
@EnableTransactionManagement
@Configuration
@EnableConfigurationProperties(MasterHikariProperties::class, ReplicaHikariProperties::class)
class DataSourceConfig {

    /* ===================== MASTER ===================== */

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource")
    fun masterProps() = DataSourceProperties()

    @Primary
    @LiquibaseDataSource
    @Bean(name = ["dataSource"])
    fun masterDs(
        @Qualifier("masterProps") props: DataSourceProperties,
        masterHikariProperties: MasterHikariProperties
    ): HikariDataSource =
        props.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
            .apply {
                poolName = masterHikariProperties.poolName ?: "payment-master"
                masterHikariProperties.maximumPoolSize?.let { maximumPoolSize = it }
                masterHikariProperties.minimumIdle?.let { minimumIdle = it }
                masterHikariProperties.connectionTimeout?.let { connectionTimeout = it }
                masterHikariProperties.idleTimeout?.let { idleTimeout = it }
                masterHikariProperties.maxLifetime?.let { maxLifetime = it }
            } as HikariDataSource


    /* ===================== REPLICA ==================== */

    @Bean
    @ConfigurationProperties("spring.replica-datasource")
    fun replicaProps() = DataSourceProperties()

    @Bean(name = ["replicaDataSource"])
    fun replicaDs(
        @Qualifier("replicaProps") props: DataSourceProperties,
        replicaHikariProperties: ReplicaHikariProperties
    ): HikariDataSource =
        props.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
            .apply {
                poolName = replicaHikariProperties.poolName ?: "payment-replica"
                replicaHikariProperties.maximumPoolSize?.let { maximumPoolSize = it }
                replicaHikariProperties.minimumIdle?.let { minimumIdle = it }
                replicaHikariProperties.connectionTimeout?.let { connectionTimeout = it }
                replicaHikariProperties.idleTimeout?.let { idleTimeout = it }
                replicaHikariProperties.maxLifetime?.let { maxLifetime = it }
            } as HikariDataSource

    /* ===================== JPA ======================== */

    @Primary
    @Bean(name = ["entityManagerFactory"])
    fun emf(
        @Qualifier("dataSource") ds: DataSource,
        jpa: JpaProperties,
    ) = LocalContainerEntityManagerFactoryBean().apply {
        dataSource = ds
        setPackagesToScan("com.dogancaglar.paymentservice.adapter.persistence")
        jpaVendorAdapter = HibernateJpaVendorAdapter()
        setJpaPropertyMap(jpa.properties)
    }

    @Bean(name = ["replicaEntityManagerFactory"])
    fun replicaEmf(
        @Qualifier("replicaDataSource") ds: DataSource,
        jpa: JpaProperties,
    ) = LocalContainerEntityManagerFactoryBean().apply {
        dataSource = ds
        setPackagesToScan("com.dogancaglar.paymentservice.adapter.persistence")
        jpaVendorAdapter = HibernateJpaVendorAdapter()
        setJpaPropertyMap(jpa.properties)
    }

    /* ===================== TX MANAGERS ================ */

    @Primary
    @Bean("transactionManager")
    fun tx(@Qualifier("entityManagerFactory") emf: EntityManagerFactory) = JpaTransactionManager(emf)

    @Bean(name = ["replicaTransactionManager"])
    fun replicaTx(@Qualifier("replicaEntityManagerFactory") emf: EntityManagerFactory) = JpaTransactionManager(emf)

    /* ===================== METRICS ==================== */

    @Bean
    fun masterMetrics(reg: MeterRegistry, @Qualifier("dataSource") ds: HikariDataSource): HikariDataSource =
        ds.apply { metricsTrackerFactory = MicrometerMetricsTrackerFactory(reg) }

    @Bean
    fun replicaMetrics(reg: MeterRegistry, @Qualifier("replicaDataSource") ds: HikariDataSource): HikariDataSource =
        ds.apply { metricsTrackerFactory = MicrometerMetricsTrackerFactory(reg) }
}

// HikariCP properties holder for master
@ConfigurationProperties("spring.datasource.hikari")
class MasterHikariProperties {
    var maximumPoolSize: Int? = null
    var minimumIdle: Int? = null
    var connectionTimeout: Long? = null
    var idleTimeout: Long? = null
    var maxLifetime: Long? = null
    var poolName: String? = null
}

// HikariCP properties holder for replica
@ConfigurationProperties("spring.replica-datasource.hikari")
class ReplicaHikariProperties {
    var maximumPoolSize: Int? = null
    var minimumIdle: Int? = null
    var connectionTimeout: Long? = null
    var idleTimeout: Long? = null
    var maxLifetime: Long? = null
    var poolName: String? = null
}
