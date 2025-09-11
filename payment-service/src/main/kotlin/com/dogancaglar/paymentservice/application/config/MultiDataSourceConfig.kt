package com.dogancaglar.paymentservice.application.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

@Configuration
class MultiDataSourceConfig {

    // -------- DataSources --------

    @Bean("webDataSource")
    @Primary
    @ConfigurationProperties("app.datasource.web")
    @LiquibaseDataSource
    fun webDataSource(): HikariDataSource = HikariDataSource()

    @Bean("outboxDataSource")
    @ConfigurationProperties("app.datasource.outbox")
    fun outboxDataSource(): HikariDataSource = HikariDataSource()

    @Bean("maintenanceDataSource")
    @ConfigurationProperties("app.datasource.maintenance")
    fun maintenanceDataSource(): HikariDataSource = HikariDataSource()

    // -------- TxManagers --------


    @Bean("webTxManager")
    @Primary
    fun webTxManager(
        @Qualifier("webDataSource") ds: DataSource,
        @org.springframework.beans.factory.annotation.Value("\${db.web.statement-timeout-ms:500}") stmtMs: Long,
        @org.springframework.beans.factory.annotation.Value("\${db.web.lock-timeout-ms:250}") lockMs: Long,
        @org.springframework.beans.factory.annotation.Value("\${db.web.idle-in-tx-timeout-ms:1800}") idleMs: Long
    ) = DBWriterTxManager(ds, stmtMs, lockMs, idleMs).apply {
        setDefaultTimeout(2)
    }

    @Bean("outboxTxManager")
    fun outboxTxManager(
        @Qualifier("outboxDataSource") ds: DataSource,
        @org.springframework.beans.factory.annotation.Value("\${db.outbox.statement-timeout-ms:2000}") stmtMs: Long,
        @org.springframework.beans.factory.annotation.Value("\${db.outbox.lock-timeout-ms:200}") lockMs: Long,
        @org.springframework.beans.factory.annotation.Value("\${db.outbox.idle-in-tx-timeout-ms:0}") idleMs: Long
    ) =
        DBWriterTxManager(ds, stmtMs, lockMs, idleMs).apply {
            setDefaultTimeout(60)

        }


    @Bean("maintenanceTxManager")
    fun maintenanceTxManager(@Qualifier("maintenanceDataSource") ds: DataSource) =
        DataSourceTransactionManager(ds).apply { setDefaultTimeout(5) } // DDL can be a bit longer

    // -------- JdbcTemplate for repos/DAOs that should use job pools --------


    @Bean("maintenanceJdbcTemplate")
    fun maintenanceJdbc(@Qualifier("maintenanceDataSource") ds: DataSource) = JdbcTemplate(ds)
}
