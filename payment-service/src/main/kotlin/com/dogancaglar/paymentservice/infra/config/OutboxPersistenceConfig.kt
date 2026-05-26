package com.dogancaglar.paymentservice.infra.config

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.outboxeventmapper.OutboxEventDispatcherMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.outboxeventmapper.OutboxEventWebMapper
import org.mybatis.spring.SqlSessionTemplate
import org.mybatis.spring.mapper.MapperFactoryBean
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OutboxPersistenceConfig {

    @Bean
    fun outboxEventDispatcherMapper(
        @Qualifier("outboxSqlSessionTemplate") template: SqlSessionTemplate
    ): MapperFactoryBean<OutboxEventDispatcherMapper> {
        val factory = MapperFactoryBean(OutboxEventDispatcherMapper::class.java)
        factory.setSqlSessionTemplate(template)
        return factory
    }

    @Bean
    fun outboxEventWebMapper(
        @Qualifier("webSqlSessionTemplate") template: SqlSessionTemplate
    ): MapperFactoryBean<OutboxEventWebMapper> {
        val factory = MapperFactoryBean(OutboxEventWebMapper::class.java)
        factory.setSqlSessionTemplate(template)
        return factory
    }
}