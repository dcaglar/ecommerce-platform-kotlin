package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentMapper
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
class PaymentMapperIntegrationTest {

    /**
     * Normalizes Instant to microsecond precision to match PostgreSQL's TIMESTAMP precision.
     * PostgreSQL stores timestamps with microsecond precision (6 decimal places),
     * but Java Instant can have nanosecond precision (9 decimal places).
     */
    private fun Instant.normalizeToMicroseconds(): Instant {
        return this.truncatedTo(java.time.temporal.ChronoUnit.MICROS)
    }

    /**
     * Normalizes PaymentEntity timestamps to microsecond precision for comparison.
     */
    private fun PaymentEntity.normalizeTimestamps(): PaymentEntity {
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
                PaymentMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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
    lateinit var paymentMapper: PaymentMapper

    private val objectMapper: ObjectMapper = JacksonUtil.createObjectMapper()

    private fun sampleEntity(id: Long = 101L): PaymentEntity {
        val now = Utc.nowInstant().normalizeToMicroseconds()
        val paymentOrderLines = listOf(
            PaymentOrderLine(
                sellerId = SellerId("seller-1"),
                amount = Amount.of(10_000, Currency("USD"))
            )
        )
        return PaymentEntity(
            paymentId = id,
            paymentIntentId = id,
            buyerId = "buyer-$id",
            orderId = "order-$id",
            totalAmountValue = 10_000,
            capturedAmountValue = 0,
            refundedAmountValue = 0,
            currency = "USD",
            status = "PENDING_AUTH",
            createdAt = now,
            updatedAt = now,
            paymentLinesJson = objectMapper.writeValueAsString(paymentOrderLines)
        )
    }

    @Test
    fun `insert and findById and getMaxPaymentId`() {
        // given
        val entity = sampleEntity(101L)

        // when
        val rows = paymentMapper.insert(entity)

        // then
        assertEquals(1, rows)

        val loaded = paymentMapper.findById(101L)
        assertNotNull(loaded)

        // normalize timestamps to match Postgres precision
        assertEquals(
            entity.normalizeTimestamps(),
            loaded!!.normalizeTimestamps()
        )

        val maxId = paymentMapper.getMaxPaymentId()
        assertEquals(101L, maxId)
    }

    @Test
    fun `update and deleteById`() {
        // given
        val entity = sampleEntity(201L)
        paymentMapper.insert(entity)

        // when – update some fields
        val updatedEntity = entity.copy(
            status = "AUTHORIZED",
            capturedAmountValue = 5_000,
            updatedAt = Utc.nowInstant().normalizeToMicroseconds()
        )
        val updatedRows = paymentMapper.update(updatedEntity)

        // then
        assertEquals(1, updatedRows)

        val reloaded = paymentMapper.findById(201L)
        assertNotNull(reloaded)
        assertEquals("AUTHORIZED", reloaded!!.status)
        assertEquals(5_000, reloaded.capturedAmountValue)

        // when – delete by id
        val deletedRows = paymentMapper.deleteById(201L)
        assertEquals(1, deletedRows)

        val afterDelete = paymentMapper.findById(201L)
        assertNull(afterDelete)
    }


}
