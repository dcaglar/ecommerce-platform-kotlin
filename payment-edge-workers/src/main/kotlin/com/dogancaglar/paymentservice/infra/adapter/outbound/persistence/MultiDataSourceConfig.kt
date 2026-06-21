package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource
import org.springframework.beans.factory.annotation.Value

@Configuration
class MultiDataSourceConfig(@Value("\${app.pod-name}") private val podName: String,
                            @Value("\${app.edge-cell-base-url}") private val baseUrl: String,
                            @Value("\${app.edge-cell-headless-service}") private val headlessService: String) {


    // If you need to change the URL format, you change this one line.
    private fun buildEdgeDbUrl(podName: String): String {
        val ordinal = podName.substringAfterLast("-")
        return "jdbc:postgresql://payment-edge-cell-${ordinal}.payment-edge-cell-headless:5432/edge-db?options=-c%20timezone=UTC"
    }

    // -------- DataSources --------

    private fun buildDynamicEdgeUrl(): String {
        val ordinal = podName.substringAfterLast("-")
        return "jdbc:postgresql://${baseUrl}-${ordinal}.${headlessService}:5432/edge-db?options=-c%20timezone=UTC"
    }

    @Bean("outboxDataSource")
    @Primary
    @LiquibaseDataSource
    @ConfigurationProperties("app.datasource.outbox")
    fun outboxDataSource(): HikariDataSource {
        return HikariDataSource().apply {
            poolName = "edge-outbox-pool"
            // We set the URL manually to bypass the YAML/Environment variable logic
            jdbcUrl = buildDynamicEdgeUrl()
            // All other properties (timeouts, pool-size) defined in YAML
            // will be automatically applied by @ConfigurationProperties after this method returns.
        }
    }

    @Bean("maintenanceDataSource")
    @ConfigurationProperties("app.datasource.maintenance")
    fun maintenanceDataSource(): HikariDataSource {
        return HikariDataSource().apply {
            poolName = "edge-maintenance-pool"
            jdbcUrl = buildDynamicEdgeUrl()
        }
    }

    @Bean("centralDataSource")
    @ConfigurationProperties("app.datasource.central")
    fun centralDataSource(@Value("\${app.datasource.central.centralDbUrl}") centralDbUrl: String): HikariDataSource {
        return HikariDataSource().apply {
            poolName = "central-edge-worker-pool"
            jdbcUrl =  centralDbUrl
            // No URL override here, it will use the value from YAML (central_db_url)
        }
    }

    // -------- TxManagers --------

    @Bean("outboxTxManager")
    @Primary
    fun outboxTxManager(
        @Qualifier("outboxDataSource") ds: DataSource,
        @Value("\${db.outbox.statement-timeout-ms:2000}") stmtMs: Long,
        @Value("\${db.outbox.lock-timeout-ms:200}") lockMs: Long,
        @Value("\${db.outbox.idle-in-tx-timeout-ms:0}") idleMs: Long
    ) = DBWriterTxManager(ds, stmtMs, lockMs, idleMs).apply {
        setDefaultTimeout(60)
    }

    @Bean("maintenanceTxManager")
    fun maintenanceTxManager(@Qualifier("maintenanceDataSource") ds: DataSource) =
        DataSourceTransactionManager(ds).apply { setDefaultTimeout(5) } // DDL can be a bit longer

    @Bean("centralTxManager")
    fun centralTxManager(
        @Qualifier("centralDataSource") ds: DataSource
    ) = DataSourceTransactionManager(ds).apply { setDefaultTimeout(60) }

    // -------- JdbcTemplate for repos/DAOs that should use job pools --------

    @Bean("maintenanceJdbcTemplate")
    fun maintenanceJdbc(@Qualifier("maintenanceDataSource") ds: DataSource) = JdbcTemplate(ds)


}
