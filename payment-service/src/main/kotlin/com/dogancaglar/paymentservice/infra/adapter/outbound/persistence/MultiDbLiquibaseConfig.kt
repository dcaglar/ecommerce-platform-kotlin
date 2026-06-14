package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.zaxxer.hikari.HikariDataSource
import liquibase.integration.spring.SpringLiquibase
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

@Configuration
@Profile("liquibase-job")
class MultiDbLiquibaseConfig{

    @Bean
    fun edgeLiquibase(@Qualifier("edgeDataSource") edgeDataSource: HikariDataSource): SpringLiquibase {
        val liquibase = SpringLiquibase()
        liquibase.setDataSource(edgeDataSource)
        liquibase.setChangeLog("classpath:/db/changelog/changelog.edge.xml")
        return liquibase
    }


    @Bean
    fun yugabyteLiquibase(@Qualifier("yugabyteDataSource") yugabyteDatasource: HikariDataSource?): SpringLiquibase {
        val liquibase = SpringLiquibase()
        liquibase.setDataSource(yugabyteDatasource)
        liquibase.setChangeLog("classpath:/db/changelog/changelog.yugabyte.xml")
        return liquibase
    }
}