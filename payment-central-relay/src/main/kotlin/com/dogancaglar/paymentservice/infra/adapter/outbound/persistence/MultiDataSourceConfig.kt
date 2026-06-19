package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

@Configuration
class MultiDataSourceConfig {

    // =========================================================================
    // 1. PRIMARY DATASOURCE PROFILE (The Core Business Ledger Engine)
    // =========================================================================

    @Primary
    @Bean("primaryDataSource")
    @ConfigurationProperties("app.datasource.primary")
    fun primaryDataSource(): DataSource {
        return DataSourceBuilder.create().type(HikariDataSource::class.java).build()
    }

    @Primary
    @Bean("centralTxManager")
    fun centralTxManager(@Qualifier("primaryDataSource") ds: DataSource): DataSourceTransactionManager {
        return DataSourceTransactionManager(ds).apply { defaultTimeout = 5 }
    }

    @Primary
    @Bean("primaryJdbcTemplate")
    fun primaryJdbcTemplate(@Qualifier("primaryDataSource") ds: DataSource): JdbcTemplate {
        return JdbcTemplate(ds)
    }

    // =========================================================================
    // 2. MAINTENANCE DATASOURCE PROFILE (Schema Checks & Migrations Only)
    // =========================================================================

    @Bean("maintenanceDataSource")
    @ConfigurationProperties("app.datasource.maintenance")
    fun maintenanceDataSource(): DataSource {
        return DataSourceBuilder.create().type(HikariDataSource::class.java).build()
    }

    @Bean("maintenanceTxManager")
    fun maintenanceTxManager(@Qualifier("maintenanceDataSource") ds: DataSource): DataSourceTransactionManager {
        return DataSourceTransactionManager(ds).apply { defaultTimeout = 5 }
    }

    @Bean("centralJdbcTemplate") // 🟢 Straightforward connection template used by your forwarder job!
    fun maintenanceJdbcTemplate(@Qualifier("maintenanceDataSource") ds: DataSource): JdbcTemplate {
        return JdbcTemplate(ds)
    }
}