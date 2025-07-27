/*
package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import org.mybatis.spring.annotation.MapperScan

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

@Configuration
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis")
class MyBatisConfig {
    // Optional: Custom type handlers, etc.
}


@Configuration
class MyBatisTransactionConfig {
    @Bean
    fun transactionManager(dataSource: DataSource): DataSourceTransactionManager =
        DataSourceTransactionManager(dataSource)
}
 */