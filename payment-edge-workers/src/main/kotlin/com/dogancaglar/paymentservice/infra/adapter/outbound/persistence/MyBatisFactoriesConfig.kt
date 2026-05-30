package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.OutboxEventDispatcherMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.CentralOutboxForwarderMapper
import org.apache.ibatis.session.ExecutorType
import org.apache.ibatis.session.SqlSessionFactory
import org.mybatis.spring.SqlSessionFactoryBean
import org.mybatis.spring.SqlSessionTemplate
import org.mybatis.spring.mapper.MapperFactoryBean
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import javax.sql.DataSource

@Configuration
class MyBatisFactoriesConfig {

    @Bean("outboxSqlSessionFactory")
    @Primary
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
    @Primary
    fun outboxSqlSessionTemplate(
        @Qualifier("outboxSqlSessionFactory") factory: SqlSessionFactory
    ): SqlSessionTemplate = SqlSessionTemplate(factory, ExecutorType.BATCH)

    @Bean("centralSqlSessionFactory")
    fun centralSqlSessionFactory(
        @Qualifier("centralDataSource") ds: DataSource
    ): SqlSessionFactory {
        val sfb = SqlSessionFactoryBean()
        sfb.setDataSource(ds)
        val resolver = PathMatchingResourcePatternResolver()
        sfb.setMapperLocations(*resolver.getResources("classpath*:mapper/**/*.xml"))
        sfb.setTypeAliasesPackage("com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity")
        return sfb.`object`!!
    }

    @Bean("centralSqlSessionTemplate")
    fun centralSqlSessionTemplate(
        @Qualifier("centralSqlSessionFactory") factory: SqlSessionFactory
    ): SqlSessionTemplate = SqlSessionTemplate(factory, ExecutorType.BATCH)

    @Bean
    fun outboxEventDispatcherMapper(
        @Qualifier("outboxSqlSessionTemplate") template: SqlSessionTemplate
    ): MapperFactoryBean<OutboxEventDispatcherMapper> {
        val factory = MapperFactoryBean(OutboxEventDispatcherMapper::class.java)
        factory.setSqlSessionTemplate(template)
        return factory
    }

    @Bean
    fun centralOutboxForwarderMapper(
        @Qualifier("centralSqlSessionTemplate") template: SqlSessionTemplate
    ): MapperFactoryBean<CentralOutboxForwarderMapper> {
        val factory = MapperFactoryBean(CentralOutboxForwarderMapper::class.java)
        factory.setSqlSessionTemplate(template)
        return factory
    }
}