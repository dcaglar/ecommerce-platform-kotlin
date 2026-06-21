package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.zaxxer.hikari.HikariDataSource
import org.apache.ibatis.session.SqlSessionFactory
import org.mybatis.spring.SqlSessionFactoryBean
import org.mybatis.spring.annotation.MapperScan
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

@ConfigurationProperties(prefix = "app.datasource.central-outbox")
data class CentralOutboxProps(
    val jdbcUrl: String,
    val username: String,
    val password: String
)

@ConfigurationProperties(prefix = "app.datasource.maintenance")
data class MaintenanceProps(
    val jdbcUrl: String,
    val username: String,
    val password: String
)

@Configuration
@EnableConfigurationProperties(CentralOutboxProps::class, MaintenanceProps::class)
@MapperScan(
    basePackages = ["com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper"],
    sqlSessionFactoryRef = "centralOutboxSqlSessionFactory"
)
class CentralOutboxDataSourceConfig {

    // =========================================================================
    // 1. CORE TRANSACTIONAL DATASOURCE PROFILE
    // =========================================================================

    @Bean("centralOutboxDataSource")
    @ConfigurationProperties("app.datasource.central-outbox")
    fun centralOutboxDataSource(props: CentralOutboxProps): HikariDataSource {
        return HikariDataSource().apply {
            poolName = "central-outbox-pool"
            jdbcUrl = props.jdbcUrl
            username = props.username
            password = props.password
        }
    }

    @Bean("centralOutboxTxManager")
    fun centralOutboxTxManager(@Qualifier("centralOutboxDataSource") ds: DataSource): DataSourceTransactionManager {
        return DataSourceTransactionManager(ds).apply { defaultTimeout = 5 }
    }

    @Bean("centralOutboxSqlSessionFactory")
    fun centralOutboxSqlSessionFactory(@Qualifier("centralOutboxDataSource") ds: DataSource): SqlSessionFactory {
        val factoryBean = SqlSessionFactoryBean()
        factoryBean.setDataSource(ds)
        
        val configuration = org.apache.ibatis.session.Configuration()
        configuration.isMapUnderscoreToCamelCase = true
        factoryBean.setConfiguration(configuration)

        val resolver = PathMatchingResourcePatternResolver()
        factoryBean.setMapperLocations(*resolver.getResources("classpath*:mapper/**/*.xml"))
        factoryBean.setTypeAliasesPackage("com.dogancaglar.common.db.entity")
        factoryBean.setTypeHandlersPackage("com.dogancaglar.common.db.typehandler")
        
        return factoryBean.`object`!!
    }

    // =========================================================================
    // 2. MAINTENANCE DATASOURCE PROFILE (Partition Checks & Schema Vacuuming)
    // =========================================================================

    @Bean("maintenanceDataSource")
    @ConfigurationProperties("app.datasource.maintenance")
    fun maintenanceDataSource(props: MaintenanceProps): HikariDataSource {
        return HikariDataSource().apply {
            poolName = "central-maintenance-pool"
            jdbcUrl = props.jdbcUrl
            username = props.username
            password = props.password
        }
    }

    @Bean("maintenanceTxManager")
    fun maintenanceTxManager(@Qualifier("maintenanceDataSource") ds: DataSource): DataSourceTransactionManager {
        return DataSourceTransactionManager(ds).apply { defaultTimeout = 5 }
    }

    @Bean("maintenanceJdbcTemplate")
    fun maintenanceJdbcTemplate(@Qualifier("maintenanceDataSource") ds: DataSource): JdbcTemplate {
        return JdbcTemplate(ds)
    }
}
