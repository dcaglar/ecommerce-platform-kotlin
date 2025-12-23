package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentIntentEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentIntentMapper
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
    
    @Autowired
    lateinit var paymentIntentMapper: PaymentIntentMapper

    private val objectMapper: ObjectMapper = JacksonUtil.createObjectMapper()
    private val paymentEntityMapper: PaymentEntityMapper = PaymentEntityMapper(objectMapper)
    private val paymentIntentEntityMapper: PaymentIntentEntityMapper = PaymentIntentEntityMapper(objectMapper)

    private fun createPaymentFromIntent(paymentId: Long, paymentIntentId: Long): PaymentEntity {
        // Create PaymentIntent with 2 payment order lines
        val paymentOrderLines = listOf(
            PaymentOrderLine(
                sellerId = SellerId("seller-1"),
                amount = Amount.of(6_000, Currency("USD"))
            ),
            PaymentOrderLine(
                sellerId = SellerId("seller-2"),
                amount = Amount.of(4_000, Currency("USD"))
            )
        )
        
        val paymentIntent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(paymentIntentId),
            buyerId = BuyerId("buyer-$paymentIntentId"),
            orderId = OrderId("order-$paymentIntentId"),
            totalAmount = Amount.of(10_000, Currency("USD")),
            paymentOrderLines = paymentOrderLines
        )
        
        // Mark as AUTHORIZED (following correct lifecycle: CREATED_PENDING -> CREATED -> PENDING_AUTH -> AUTHORIZED)
        val authorizedIntent = paymentIntent
            .markAsCreated()
            .markAuthorizedPending()
            .markAuthorized()
        
        // Save PaymentIntent to database
        paymentIntentMapper.insert(paymentIntentEntityMapper.toEntity(authorizedIntent))
        
        // Create Payment from authorized PaymentIntent
        val payment = Payment.fromAuthorizedIntent(
            paymentId = PaymentId(paymentId),
            intent = authorizedIntent
        )
        
        // Convert to entity
        return paymentEntityMapper.toEntity(payment)
    }

    @Test
    fun `insert and findById and getMaxPaymentId`() {
        // given - create payment intent with 2 payment order lines and create payment from it
        val paymentId = 101L
        val paymentIntentId = 201L
        val entity = createPaymentFromIntent(paymentId, paymentIntentId)

        // when
        val rows = paymentMapper.insert(entity)

        // then
        assertEquals(1, rows)

        val loaded = paymentMapper.findById(paymentId)
        assertNotNull(loaded)

        // normalize timestamps to match Postgres precision
        val normalizedEntity = entity.normalizeTimestamps()
        val normalizedLoaded = loaded!!.normalizeTimestamps()
        
        // Compare all fields except paymentLinesJson (JSON property order may differ)
        assertEquals(normalizedEntity.paymentId, normalizedLoaded.paymentId)
        assertEquals(normalizedEntity.paymentIntentId, normalizedLoaded.paymentIntentId)
        assertEquals(normalizedEntity.buyerId, normalizedLoaded.buyerId)
        assertEquals(normalizedEntity.orderId, normalizedLoaded.orderId)
        assertEquals(normalizedEntity.totalAmountValue, normalizedLoaded.totalAmountValue)
        assertEquals(normalizedEntity.currency, normalizedLoaded.currency)
        assertEquals(normalizedEntity.capturedAmountValue, normalizedLoaded.capturedAmountValue)
        assertEquals(normalizedEntity.refundedAmountValue, normalizedLoaded.refundedAmountValue)
        assertEquals(normalizedEntity.status, normalizedLoaded.status)
        
        // Compare timestamps with tolerance for microsecond differences
        // PostgreSQL may store/retrieve timestamps with slight precision differences
        val createdAtDiff = java.time.Duration.between(
            normalizedEntity.createdAt, normalizedLoaded.createdAt
        ).abs()
        val updatedAtDiff = java.time.Duration.between(
            normalizedEntity.updatedAt, normalizedLoaded.updatedAt
        ).abs()
        assertTrue(createdAtDiff.toMillis() < 100, 
            "createdAt difference too large: $createdAtDiff")
        assertTrue(updatedAtDiff.toMillis() < 100, 
            "updatedAt difference too large: $updatedAtDiff")
        
        // Compare JSON by parsing to JSON nodes (ignores property order)
        val expectedJsonNode = objectMapper.readTree(normalizedEntity.paymentLinesJson)
        val loadedJsonNode = objectMapper.readTree(normalizedLoaded.paymentLinesJson)
        assertEquals(expectedJsonNode, loadedJsonNode)

        val maxId = paymentMapper.getMaxPaymentId()
        assertEquals(paymentId, maxId)
    }

    @Test
    fun `update and deleteById`() {
        // given - create payment intent with 2 payment order lines and create payment from it
        val paymentId = 201L
        val paymentIntentId = 202L
        val entity = createPaymentFromIntent(paymentId, paymentIntentId)
        paymentMapper.insert(entity)

        // when – update some fields
        val updatedEntity = entity.copy(
            status = "NOT_CAPTURED",
            capturedAmountValue = 5_000,
            updatedAt = Utc.nowInstant().normalizeToMicroseconds()
        )
        val updatedRows = paymentMapper.update(updatedEntity)

        // then
        assertEquals(1, updatedRows)

        val reloaded = paymentMapper.findById(201L)
        assertNotNull(reloaded)
        assertEquals("NOT_CAPTURED", reloaded!!.status)
        assertEquals(5_000, reloaded.capturedAmountValue)

        // when – delete by id
        val deletedRows = paymentMapper.deleteById(201L)
        assertEquals(1, deletedRows)

        val afterDelete = paymentMapper.findById(201L)
        assertNull(afterDelete)
    }


}
