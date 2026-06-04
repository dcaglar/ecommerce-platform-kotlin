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
class MultiDataSourceConfig {

    // -------- DataSources --------

    @Bean("outboxDataSource")
    @Primary
    @LiquibaseDataSource
    @ConfigurationProperties("app.datasource.outbox")
    fun outboxDataSource(): HikariDataSource = HikariDataSource().apply { poolName = "edge-outbox-pool" }

    @Bean("maintenanceDataSource")
    @ConfigurationProperties("app.datasource.maintenance")
    fun maintenanceDataSource(): HikariDataSource = HikariDataSource().apply { poolName = "edge-maintenance-pool" }

    @Bean("centralDataSource")
    @ConfigurationProperties("app.datasource.central")
    fun centralDataSource(): HikariDataSource = HikariDataSource().apply { poolName = "central-edge-worker-pool" }

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
