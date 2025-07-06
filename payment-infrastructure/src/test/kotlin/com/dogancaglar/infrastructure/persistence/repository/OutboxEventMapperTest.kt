package com.dogancaglar.infrastructure.persistence.repository

import com.dogancaglar.infrastructure.persistence.entity.OutboxEventEntity
import org.apache.ibatis.mapping.VendorDatabaseIdProvider
import org.apache.ibatis.session.SqlSessionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mybatis.spring.SqlSessionFactoryBean
import org.mybatis.spring.annotation.MapperScan
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDateTime
import java.util.*

@Configuration
@MapperScan("com.dogancaglar.infrastructure.persistence.repository")
class MybatisTestConfig {
    @Bean
    fun databaseIdProvider(): VendorDatabaseIdProvider {
        val provider = VendorDatabaseIdProvider()
        provider.setProperties(java.util.Properties().apply {
            setProperty("H2", "h2")
            setProperty("PostgreSQL", "postgresql")
        })
        return provider
    }

    @Bean
    fun sqlSessionFactory(dataSource: javax.sql.DataSource): SqlSessionFactory {
        val sessionFactory = SqlSessionFactoryBean()
        sessionFactory.setDataSource(dataSource)
        sessionFactory.setMapperLocations(
            *PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml")
        )
        sessionFactory.setDatabaseIdProvider(databaseIdProvider())
        return sessionFactory.`object`!!
    }
}

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = ["classpath:schema-test.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@ContextConfiguration(classes = [MybatisTestConfig::class])
class OutboxEventMapperTest @Autowired constructor(
    val outboxEventMapper: OutboxEventMapper
) {
    private lateinit var testEvent: OutboxEventEntity

    @BeforeEach
    fun setup() {
        testEvent = OutboxEventEntity(
            eventId = UUID.randomUUID(),
            eventType = "TEST_TYPE",
            aggregateId = "agg-1",
            payload = "{\"foo\":\"bar\"}",
            status = "NEW",
            createdAt = LocalDateTime.now()
        )
        outboxEventMapper.insert(testEvent)
    }

    @Test
    fun `findByStatus should return correct mapping`() {
        val result = outboxEventMapper.findByStatus("NEW")
        assertThat(result).isNotEmpty
        val event = result.first()
        assertThat(event.eventId).isEqualTo(testEvent.eventId)
        assertThat(event.eventType).isEqualTo(testEvent.eventType)
        assertThat(event.aggregateId).isEqualTo(testEvent.aggregateId)
        assertThat(event.payload).isEqualTo(testEvent.payload)
        assertThat(event.status).isEqualTo(testEvent.status)
    }

    @Test
    fun `countByStatus should return correct count`() {
        val count = outboxEventMapper.countByStatus("NEW")
        assertThat(count).isGreaterThanOrEqualTo(1)
    }


    @Test
    fun `findBatchForDispatch should return correct mapping`() {
        val result = outboxEventMapper.findBatchForDispatch(1)
        assertThat(result).isNotEmpty
        val event = result.first()
        assertThat(event.eventId).isEqualTo(testEvent.eventId)
    }

}
