package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.web.OutboxEventMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.outbox.OutboxPollerMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mybatis.spring.annotation.MapperScan
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import com.dogancaglar.common.time.Utc

@Tag("integration")
@MybatisTest
@ContextConfiguration(classes = [InfraTestBoot::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.liquibase.enabled=false"])
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.web")
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.outbox")
class OutboxPollerMapperIntegrationTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ddl = OutboxPollerMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
            postgres.createConnection("").use { c -> c.createStatement().execute(ddl) }
        }

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        init { postgres.start() }

        @DynamicPropertySource
        @JvmStatic
        fun datasourceProps(reg: DynamicPropertyRegistry) {
            reg.add("spring.datasource.url", postgres::getJdbcUrl)
            reg.add("spring.datasource.username", postgres::getUsername)
            reg.add("spring.datasource.password", postgres::getPassword)
            reg.add("spring.datasource.driver-class-name", postgres::getDriverClassName)
        }
    }

    @Autowired
    lateinit var outboxEventMapper: OutboxEventMapper // for setup

    @Autowired
    lateinit var pollerMapper: OutboxPollerMapper // for testing

    private fun newEvent(oeid: Long, status: String = "NEW") = OutboxEventEntity(
        oeid = oeid,
        eventType = "PAYMENT_ORDER_CREATED",
        aggregateId = "agg-1",
        payload = "{\"foo\": \"bar\"}",
        status = status,
        createdAt = Utc.nowInstant(),
        updatedAt = Utc.nowInstant()
    )

    @Test
    fun `findBatchForDispatch claims NEW events`() {
        outboxEventMapper.insertOutboxEvent(newEvent(101L))
        outboxEventMapper.insertOutboxEvent(newEvent(102L))

        val claimed = pollerMapper.findBatchForDispatch(2, "worker-test")
        assertEquals(2, claimed.size)
        assertTrue(claimed.all { it.status == "PROCESSING" })
    }

    @Test
    fun `countByStatus returns correct count`() {
        outboxEventMapper.insertOutboxEvent(newEvent(201L, "NEW"))
        assertEquals(1, pollerMapper.countByStatus("NEW"))
    }
}
