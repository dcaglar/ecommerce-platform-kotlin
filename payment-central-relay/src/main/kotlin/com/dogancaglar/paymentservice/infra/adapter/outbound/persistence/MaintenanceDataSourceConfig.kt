package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

@Configuration
class MaintenanceDataSourceConfig {

    @Bean("maintenanceDataSource")
    @ConfigurationProperties("app.datasource.maintenance")
    fun maintenanceDataSource(): HikariDataSource = HikariDataSource().apply { poolName = "central-maint-pool" }

    @Bean("maintenanceTxManager")
    fun maintenanceTxManager(@Qualifier("maintenanceDataSource") ds: DataSource) =
        DataSourceTransactionManager(ds).apply { setDefaultTimeout(5) }

    @Bean("maintenanceJdbcTemplate")
    fun maintenanceJdbc(@Qualifier("maintenanceDataSource") ds: DataSource) = JdbcTemplate(ds)
}
