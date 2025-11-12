package com.dogancaglar.paymentservice.application.config

import org.apache.ibatis.session.ExecutorType
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
    fun webSqlSessionFactory(@Qualifier("webDataSource") ds: DataSource): org.apache.ibatis.session.SqlSessionFactory {
        val sfb = org.mybatis.spring.SqlSessionFactoryBean()
        sfb.setDataSource(ds)
        val resolver = PathMatchingResourcePatternResolver()
        sfb.setMapperLocations(*resolver.getResources("classpath*:mapper/**/*.xml"))
        sfb.setTypeAliasesPackage("com.dogancaglar.paymentservice.adapter.outbound.persistence.entity")
        return sfb.`object`!!
    }


    @Bean("outboxSqlSessionFactory")
  fun outboxSqlSessionFactory(
    @Qualifier("outboxDataSource") ds: DataSource
  ): org.apache.ibatis.session.SqlSessionFactory {
    val sfb = org.mybatis.spring.SqlSessionFactoryBean()
    sfb.setDataSource(ds)
      val resolver = PathMatchingResourcePatternResolver()
    sfb.setMapperLocations(*resolver.getResources("classpath*:mapper/**/*.xml"))
    sfb.setTypeAliasesPackage("com.dogancaglar.paymentservice.adapter.outbound.persistence.entity")
    return sfb.`object`!!
  }

    @Bean("outboxSqlSessionTemplate")
    fun outboxSqlSessionTemplate(
        @Qualifier("outboxSqlSessionFactory") factory: org.apache.ibatis.session.SqlSessionFactory
    ): SqlSessionTemplate = SqlSessionTemplate(factory, ExecutorType.BATCH)
}