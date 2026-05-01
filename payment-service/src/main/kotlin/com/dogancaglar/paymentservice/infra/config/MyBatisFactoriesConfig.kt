package com.dogancaglar.paymentservice.infra.config

import org.apache.ibatis.session.ExecutorType
import org.apache.ibatis.session.SqlSessionFactory
import org.mybatis.spring.SqlSessionFactoryBean
import org.mybatis.spring.SqlSessionTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import javax.sql.DataSource

@Configuration
class MyBatisFactoriesConfig {


    @Bean("webSqlSessionFactory")
    @Primary
    fun webSqlSessionFactory(@Qualifier("webDataSource") ds: DataSource): SqlSessionFactory {
        val sfb = SqlSessionFactoryBean()
        sfb.setDataSource(ds)
        val resolver = PathMatchingResourcePatternResolver()
        sfb.setMapperLocations(*resolver.getResources("classpath*:mapper/**/*.xml"))
        sfb.setTypeAliasesPackage("com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity")
        return sfb.`object`!!
    }

    @Bean("outboxSqlSessionFactory")
    fun outboxSqlSessionFactory(
        @Qualifier("outboxDataSource") ds: DataSource
    ): SqlSessionFactory {
        val sfb = SqlSessionFactoryBean()
        sfb.setDataSource(ds)
        val resolver = PathMatchingResourcePatternResolver()
        sfb.setMapperLocations(*resolver.getResources("classpath*:mapper/**/*.xml"))
        sfb.setTypeAliasesPackage("com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity")
        return sfb.`object`!!
    }

    @Bean("outboxSqlSessionTemplate")
    fun outboxSqlSessionTemplate(
        @Qualifier("outboxSqlSessionFactory") factory: SqlSessionFactory
    ): SqlSessionTemplate = SqlSessionTemplate(factory, ExecutorType.BATCH)
}