package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentIntentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentIntentMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
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
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.serialization.JacksonUtil
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant

@Tag("integration")
@MybatisTest
@ContextConfiguration(classes = [InfraTestBoot::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = ["spring.liquibase.enabled=false"])
@MapperScan("com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis")
class PaymentIntentMapperIntegrationTest {

    /**
     * Normalizes Instant to microsecond precision to match PostgreSQL's TIMESTAMP precision.
     * PostgreSQL stores timestamps with microsecond precision (6 decimal places),
     * but Java Instant can have nanosecond precision (9 decimal places).
     */
    private fun Instant.normalizeToMicroseconds(): Instant {
        return this.truncatedTo(java.time.temporal.ChronoUnit.MICROS)
    }

    /**
     * Normalizes PaymentIntentEntity timestamps to microsecond precision for comparison.
     */
    private fun PaymentIntentEntity.normalizeTimestamps(): PaymentIntentEntity {
        return this.copy(
            createdAt = this.createdAt.normalizeToMicroseconds(),
            updatedAt = this.updatedAt.normalizeToMicroseconds()
        )
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ddl =
                PaymentIntentMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
            postgres.createConnection("").use { c -> c.createStatement().execute(ddl) }
        }

        init {
            postgres.start()
        }

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
    lateinit var paymentIntentMapper: PaymentIntentMapper

    private val objectMapper: ObjectMapper = JacksonUtil.createObjectMapper()

    private fun sampleEntity(id: Long = 101L): PaymentIntentEntity {
        val now = Utc.nowInstant().normalizeToMicroseconds()
        val paymentOrderLines = listOf(
            PaymentOrderLine(
                sellerId = SellerId("seller-1"),
                amount = Amount.of(10_000, Currency("USD"))
            )
        )
        return PaymentIntentEntity(
            paymentIntentId = id,
            buyerId = "buyer-$id",
            orderId = "order-$id",
            totalAmountValue = 10_000,
            currency = "USD",
            status = "CREATED",
            createdAt = now,
            updatedAt = now,
            paymentLinesJson = objectMapper.writeValueAsString(paymentOrderLines)
        )
    }

    @Test
    fun `insert and findById and getMaxPaymentIntentId`() {
        // given
        val entity = sampleEntity(101L)

        // when
        val rows = paymentIntentMapper.insert(entity)

        // then
        assertEquals(1, rows)

        val loaded = paymentIntentMapper.findById(101L)
        assertNotNull(loaded)

        // normalize timestamps to match Postgres precision
        val normalizedEntity = entity.normalizeTimestamps()
        val normalizedLoaded = loaded!!.normalizeTimestamps()
        
        // Compare all fields except paymentLinesJson (JSON property order may differ)
        assertEquals(normalizedEntity.paymentIntentId, normalizedLoaded.paymentIntentId)
        assertEquals(normalizedEntity.buyerId, normalizedLoaded.buyerId)
        assertEquals(normalizedEntity.orderId, normalizedLoaded.orderId)
        assertEquals(normalizedEntity.totalAmountValue, normalizedLoaded.totalAmountValue)
        assertEquals(normalizedEntity.currency, normalizedLoaded.currency)
        assertEquals(normalizedEntity.status, normalizedLoaded.status)
        assertEquals(normalizedEntity.createdAt, normalizedLoaded.createdAt)
        assertEquals(normalizedEntity.updatedAt, normalizedLoaded.updatedAt)
        
        // Compare JSON by parsing to JSON nodes (ignores property order)
        val expectedJsonNode = objectMapper.readTree(normalizedEntity.paymentLinesJson)
        val loadedJsonNode = objectMapper.readTree(normalizedLoaded.paymentLinesJson)
        assertEquals(expectedJsonNode, loadedJsonNode)

        val maxId = paymentIntentMapper.getMaxPaymentIntentId()
        assertEquals(101L, maxId)
    }

    @Test
    fun `update and deleteById`() {
        // given
        val entity = sampleEntity(201L)
        paymentIntentMapper.insert(entity)

        // when – update some fields
        val updatedEntity = entity.copy(
            status = "AUTHORIZED",
            updatedAt = Utc.nowInstant().normalizeToMicroseconds()
        )
        val updatedRows = paymentIntentMapper.update(updatedEntity)

        // then
        assertEquals(1, updatedRows)

        val reloaded = paymentIntentMapper.findById(201L)
        assertNotNull(reloaded)
        assertEquals("AUTHORIZED", reloaded!!.status)

        // when – delete by id
        val deletedRows = paymentIntentMapper.deleteById(201L)
        assertEquals(1, deletedRows)

        val afterDelete = paymentIntentMapper.findById(201L)
        assertNull(afterDelete)
    }
}

