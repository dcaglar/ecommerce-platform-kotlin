package com.dogancaglar.infrastructure.config.mybatis

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

@Configuration
class MyBatisTransactionConfig {
    @Bean
    fun transactionManager(dataSource: DataSource): DataSourceTransactionManager =
        DataSourceTransactionManager(dataSource)
}