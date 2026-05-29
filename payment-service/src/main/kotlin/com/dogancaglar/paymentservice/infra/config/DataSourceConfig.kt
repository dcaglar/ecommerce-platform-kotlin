package com.dogancaglar.paymentservice.infra.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableTransactionManagement
class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.web")
    fun dataSource(): HikariDataSource = HikariDataSource()
}
