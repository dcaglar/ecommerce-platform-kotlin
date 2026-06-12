package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.zaxxer.hikari.HikariDataSource
import liquibase.integration.spring.SpringLiquibase
import org.apache.ibatis.session.SqlSessionFactory
import org.mybatis.spring.SqlSessionFactoryBean
import org.mybatis.spring.annotation.MapperScan
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

@Configuration
@MapperScan(
    basePackages = ["com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.edge"],
    sqlSessionFactoryRef = "edgeSqlSessionFactory"
)
@MapperScan(
    basePackages = ["com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.yugabyte"],
    sqlSessionFactoryRef = "yugabyteSqlSessionFactory"
)
class DataSourceConfig {

    // --- EDGE DB (Primary) ---

    @Bean(name = ["edgeDataSource"])
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.edge")
    fun edgeDataSource(): DataSource {
        return HikariDataSource()
    }

    @Bean(name = ["edgeTransactionManager"])
    @Primary
    fun edgeTransactionManager(@Qualifier("edgeDataSource") dataSource: DataSource): DataSourceTransactionManager {
        return DataSourceTransactionManager(dataSource)
    }

    @Bean(name = ["edgeSqlSessionFactory"])
    @Primary
    fun edgeSqlSessionFactory(@Qualifier("edgeDataSource") dataSource: DataSource): SqlSessionFactory {
        val sessionFactory = SqlSessionFactoryBean()
        sessionFactory.setDataSource(dataSource)
        sessionFactory.setTypeAliasesPackage("com.dogancaglar.common.db.entity")
        sessionFactory.setTypeHandlersPackage("com.dogancaglar.common.db.typehandler")
        sessionFactory.setMapperLocations(*PathMatchingResourcePatternResolver().getResources("classpath*:mapper/edge/**/*.xml"))
        return sessionFactory.`object`!!
    }

    @Bean
    fun edgeLiquibase(
        @Qualifier("edgeDataSource") dataSource: DataSource
    ): SpringLiquibase {
        val liquibase = SpringLiquibase()
        liquibase.dataSource = dataSource
        liquibase.changeLog = "classpath:db/changelog/changelog.edge.xml"
        return liquibase
    }

    // --- YUGABYTE DB (Idempotency) ---

    @Bean(name = ["yugabyteDataSource"])
    @ConfigurationProperties(prefix = "spring.datasource.yugabyte")
    fun yugabyteDataSource(): DataSource {
        return HikariDataSource()
    }

    @Bean(name = ["yugabyteTransactionManager"])
    fun yugabyteTransactionManager(@Qualifier("yugabyteDataSource") dataSource: DataSource): DataSourceTransactionManager {
        return DataSourceTransactionManager(dataSource)
    }

    @Bean(name = ["yugabyteSqlSessionFactory"])
    fun yugabyteSqlSessionFactory(@Qualifier("yugabyteDataSource") dataSource: DataSource): SqlSessionFactory {
        val sessionFactory = SqlSessionFactoryBean()
        sessionFactory.setDataSource(dataSource)
        sessionFactory.setTypeAliasesPackage("com.dogancaglar.common.db.entity")
        sessionFactory.setTypeHandlersPackage("com.dogancaglar.common.db.typehandler")
        sessionFactory.setMapperLocations(*PathMatchingResourcePatternResolver().getResources("classpath*:mapper/yugabyte/**/*.xml"))
        return sessionFactory.`object`!!
    }

    @Bean
    fun yugabyteLiquibase(
        @Qualifier("yugabyteDataSource") dataSource: DataSource
    ): SpringLiquibase {
        val liquibase = SpringLiquibase()
        liquibase.dataSource = dataSource
        liquibase.changeLog = "classpath:db/changelog/changelog.yugabyte.xml"
        return liquibase
    }
}
