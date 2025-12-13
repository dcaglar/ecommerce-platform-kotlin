package com.dogancaglar.paymentservice.mybatis

import com.dogancaglar.paymentservice.InfraTestBoot
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentOrderEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentOrderMapper
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
class PaymentOrderMapperIntegrationTest {

    /**
     * Normalizes Instant to microsecond precision to match PostgreSQL's TIMESTAMP precision.
     * PostgreSQL stores timestamps with microsecond precision (6 decimal places),
     * but Java Instant can have nanosecond precision (9 decimal places).
     */
    private fun Instant.normalizeToMicroseconds(): Instant {
        return this.truncatedTo(java.time.temporal.ChronoUnit.MICROS)
    }

    /**
     * Normalizes PaymentOrderEntity timestamps to microsecond precision for comparison.
     */
    private fun PaymentOrderEntity.normalizeTimestamps(): PaymentOrderEntity {
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
                PaymentOrderMapperIntegrationTest::class.java.classLoader.getResource("schema-test.sql")!!.readText()
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
    lateinit var paymentOrderMapper: PaymentOrderMapper

    private val objectMapper: ObjectMapper = JacksonUtil.createObjectMapper()

    private fun upsertPayment(paymentId: Long) {
        val now = Utc.nowInstant()
        val paymentOrderLines = listOf(
            PaymentOrderLine(
                sellerId = SellerId("seller-1"),
                amount = Amount.of(50_00, Currency("USD"))
            )
        )
        paymentMapper.insert(
            PaymentEntity(
                paymentId = paymentId,
                paymentIntentId = paymentId,
                buyerId = "buyer-$paymentId",
                orderId = "order-$paymentId",
                totalAmountValue = 50_00,
                capturedAmountValue = 0,
                refundedAmountValue = 0,
                currency = "USD",
                status = "PENDING_AUTH",
                createdAt = now,
                updatedAt = now,
                paymentLinesJson = objectMapper.writeValueAsString(paymentOrderLines)
            )
        )
    }

    private fun paymentOrderEntity(
        id: Long,
        paymentId: Long = 1001L,
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING,
        retryCount: Int = 0,
        createdAt: Instant = Utc.nowInstant(),
        updatedAt: Instant = createdAt
    ) = PaymentOrderEntity(
        paymentOrderId = id,
        paymentId = paymentId,
        sellerId = "seller-$paymentId",
        amountValue = 10_00,
        amountCurrency = "USD",
        status = status,
        createdAt = createdAt.normalizeToMicroseconds(),
        updatedAt = updatedAt.normalizeToMicroseconds(),
        retryCount = retryCount
    )

    @Test
    fun `basic CRUD and counters`() {
        upsertPayment(1001L)

        val entity = paymentOrderEntity(201L)
        val inserted = paymentOrderMapper.insert(entity)
        assertEquals(1, inserted)

        val byId = paymentOrderMapper.findByPaymentOrderId(201L)
        assertEquals(1, byId.size)
        // Normalize timestamps to microsecond precision for comparison (PostgreSQL precision)
        assertEquals(entity.normalizeTimestamps(), byId.first().normalizeTimestamps())

        val byPayment = paymentOrderMapper.findByPaymentId(1001L)
        assertEquals(1, byPayment.size)

        assertEquals(1L, paymentOrderMapper.countByPaymentId(1001L))
        assertEquals(
            1L,
            paymentOrderMapper.countByPaymentIdAndStatusIn(
                paymentId = 1001L,
                statuses = listOf(PaymentOrderStatus.INITIATED_PENDING.name)
            )
        )
        assertEquals(
            false,
            paymentOrderMapper.existsByPaymentIdAndStatus(
                1001L,
                PaymentOrderStatus.CAPTURED.name
            )
        )
        assertEquals(
            true,
            paymentOrderMapper.existsByPaymentIdAndStatus(
                1001L,
                PaymentOrderStatus.INITIATED_PENDING.name
            )
        )

        assertEquals(201L, paymentOrderMapper.getMaxPaymentOrderId())
    }

    @Test
    fun `updateReturningIdempotent respects terminal statuses`() {
        upsertPayment(2001L)
        val base = paymentOrderEntity(
            id = 301L,
            paymentId = 2001L,
            status = PaymentOrderStatus.CAPTURE_REQUESTED,
            retryCount = 0  // CAPTURE_REQUESTED requires retryCount = 0
        )
        paymentOrderMapper.insert(base)

        val updated = paymentOrderMapper.updateReturningIdempotent(
            base.copy(
                status = PaymentOrderStatus.CAPTURE_FAILED,
                retryCount = 3,
                updatedAt = base.updatedAt.plusSeconds(300) // 5 minutes
            )
        )
        assertNotNull(updated)
        assertEquals(PaymentOrderStatus.CAPTURE_FAILED, updated!!.status)
        // When transitioning to terminal status, retry_count is frozen at the OLD value (0), not the NEW value (3)
        // This is the correct behavior: terminal statuses freeze retry_count
        assertEquals(0, updated.retryCount)

        // Attempt to override terminal status CAPTURE_FAILED -> CAPTURED should remain CAPTURE_FAILED
        // SQL protects terminal statuses: once terminal, cannot be changed to another terminal status
        val attemptToChangeTerminal = paymentOrderMapper.updateReturningIdempotent(
            updated.copy(
                status = PaymentOrderStatus.CAPTURED,
                updatedAt = updated.updatedAt.plusSeconds(300) // 5 minutes
            )
        )
        assertNotNull(attemptToChangeTerminal)
        // SQL protection: terminal status CAPTURE_FAILED cannot be changed to CAPTURED
        assertEquals(PaymentOrderStatus.CAPTURE_FAILED, attemptToChangeTerminal!!.status)

        // Now test the other direction: create a CAPTURED order and try to change it to CAPTURE_FAILED
        val captured = paymentOrderMapper.updateReturningIdempotent(
            updated.copy(
                status = PaymentOrderStatus.CAPTURED,
                updatedAt = updated.updatedAt.plusSeconds(600) // 10 minutes
            )
        )
        // This should also fail - we're still trying to change from CAPTURE_FAILED to CAPTURED
        assertNotNull(captured)
        assertEquals(PaymentOrderStatus.CAPTURE_FAILED, captured!!.status)
        
        // To test CAPTURED protection, we need to start with a CAPTURED status
        // Insert a new order with CAPTURED status
        val capturedOrder = paymentOrderEntity(
            id = 302L,
            paymentId = 2001L,
            status = PaymentOrderStatus.CAPTURED,
            retryCount = 0
        )
        paymentOrderMapper.insert(capturedOrder)
        
        // Attempt to change CAPTURED to CAPTURE_FAILED - should remain CAPTURED
        val afterTerminal = paymentOrderMapper.updateReturningIdempotent(
            capturedOrder.copy(
                status = PaymentOrderStatus.CAPTURE_FAILED,
                updatedAt = capturedOrder.updatedAt.plusSeconds(600) // 10 minutes
            )
        )
        // status stays CAPTURED (terminal status protection)
        assertNotNull(afterTerminal)
        assertEquals(PaymentOrderStatus.CAPTURED, afterTerminal!!.status)
        assertEquals(capturedOrder.retryCount, afterTerminal.retryCount)
    }

    @Test
    fun `insertAllIgnore skips duplicates`() {
        upsertPayment(3001L)
        val first = paymentOrderEntity(id = 401L, paymentId = 3001L)
        val second = paymentOrderEntity(id = 402L, paymentId = 3001L)
        val inserts = paymentOrderMapper.insertAllIgnore(listOf(first, second))
        assertEquals(2, inserts)

        val retry = paymentOrderMapper.insertAllIgnore(listOf(first))
        assertEquals(0, retry)
    }

    @Test
    fun `deleteAll clears table`() {
        upsertPayment(4001L)
        paymentOrderMapper.insert(paymentOrderEntity(id = 501L, paymentId = 4001L))
        assertEquals(1, paymentOrderMapper.countAll())

        paymentOrderMapper.deleteAll()
        assertEquals(0, paymentOrderMapper.countAll())
        assertNull(paymentOrderMapper.updateReturningIdempotent(paymentOrderEntity(501L)))
    }

    @Test
    fun `updateReturningIdempotentInitialCaptureRequest updates only INITIATED_PENDING with retry_count=0`() {
        upsertPayment(5001L)
        val baseTime = Utc.nowInstant().normalizeToMicroseconds()
        
        // Test 1: Successfully updates when status is INITIATED_PENDING and retry_count=0
        val eligible = paymentOrderEntity(
            id = 601L,
            paymentId = 5001L,
            status = PaymentOrderStatus.INITIATED_PENDING,
            retryCount = 0,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        paymentOrderMapper.insert(eligible)
        
        val futureTime = baseTime.plusSeconds(3600) // 1 hour
        val futureTimeLocal = Utc.fromInstant(futureTime)
        val updated = paymentOrderMapper.updateReturningIdempotentInitialCaptureRequest(601L, futureTimeLocal)
        
        assertNotNull(updated)
        assertEquals(PaymentOrderStatus.CAPTURE_REQUESTED, updated!!.status)
        assertEquals(0, updated.retryCount)
        // The GREATEST function should return futureTime since it's greater than baseTime
        // With InstantTypeHandler, TIMESTAMP WITHOUT TIME ZONE is always interpreted as UTC
        // Normalize to microsecond precision for comparison (PostgreSQL precision)
        assertEquals(futureTime.normalizeToMicroseconds(), updated.updatedAt.normalizeToMicroseconds())
        
        // Test 2: Returns null when status is not INITIATED_PENDING
        val nonEligibleStatus = paymentOrderEntity(
            id = 602L,
            paymentId = 5001L,
            status = PaymentOrderStatus.CAPTURE_REQUESTED,
            retryCount = 0,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        paymentOrderMapper.insert(nonEligibleStatus)
        
        val notUpdated1 = paymentOrderMapper.updateReturningIdempotentInitialCaptureRequest(602L, futureTimeLocal)
        assertNull(notUpdated1)
        
        // Verify status didn't change
        val stillCaptureRequested = paymentOrderMapper.findByPaymentOrderId(602L).first()
        assertEquals(PaymentOrderStatus.CAPTURE_REQUESTED, stillCaptureRequested.status)
        
        // Test 3: Returns null when retry_count != 0
        val nonEligibleRetry = paymentOrderEntity(
            id = 603L,
            paymentId = 5001L,
            status = PaymentOrderStatus.INITIATED_PENDING,
            retryCount = 1,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        paymentOrderMapper.insert(nonEligibleRetry)
        
        val notUpdated2 = paymentOrderMapper.updateReturningIdempotentInitialCaptureRequest(603L, futureTimeLocal)
        assertNull(notUpdated2)
        
        // Verify status didn't change
        val stillInitiated = paymentOrderMapper.findByPaymentOrderId(603L).first()
        assertEquals(PaymentOrderStatus.INITIATED_PENDING, stillInitiated.status)
        assertEquals(1, stillInitiated.retryCount)
        
        // Test 4: GREATEST logic for updated_at - if provided timestamp is older, keeps existing
        val baseTimePlus2h = baseTime.plusSeconds(7200) // 2 hours
        val eligible2 = paymentOrderEntity(
            id = 604L,
            paymentId = 5001L,
            status = PaymentOrderStatus.INITIATED_PENDING,
            retryCount = 0,
            createdAt = baseTime,
            updatedAt = baseTimePlus2h
        )
        paymentOrderMapper.insert(eligible2)
        
        val pastTime = baseTime.plusSeconds(1800) // 30 minutes
        val pastTimeLocal = Utc.fromInstant(pastTime)
        val updated2 = paymentOrderMapper.updateReturningIdempotentInitialCaptureRequest(604L, pastTimeLocal)
        
        assertNotNull(updated2)
        assertEquals(PaymentOrderStatus.CAPTURE_REQUESTED, updated2!!.status)
        // updated_at should be the greater of the two (existing: baseTime+2h, provided: baseTime+30m)
        // With InstantTypeHandler, TIMESTAMP WITHOUT TIME ZONE is always interpreted as UTC
        // Normalize to microsecond precision for comparison (PostgreSQL precision)
        assertEquals(baseTimePlus2h.normalizeToMicroseconds(), updated2.updatedAt.normalizeToMicroseconds())
        
        // Test 5: Does not update when status is CAPTURE_FAILED
        val captureFailed = paymentOrderEntity(
            id = 605L,
            paymentId = 5001L,
            status = PaymentOrderStatus.CAPTURE_FAILED,
            retryCount = 0,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        paymentOrderMapper.insert(captureFailed)
        
        val notUpdated3 = paymentOrderMapper.updateReturningIdempotentInitialCaptureRequest(605L, futureTimeLocal)
        assertNull(notUpdated3)
        
        val stillCaptureFailed = paymentOrderMapper.findByPaymentOrderId(605L).first()
        assertEquals(PaymentOrderStatus.CAPTURE_FAILED, stillCaptureFailed.status)
        // Normalize to microsecond precision for comparison (PostgreSQL precision)
        assertEquals(baseTime.normalizeToMicroseconds(), stillCaptureFailed.updatedAt.normalizeToMicroseconds()) // updated_at should remain unchanged
        
        // Test 6: Does not update when status is CAPTURED
        val captured = paymentOrderEntity(
            id = 606L,
            paymentId = 5001L,
            status = PaymentOrderStatus.CAPTURED,
            retryCount = 0,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        paymentOrderMapper.insert(captured)
        
        val notUpdated4 = paymentOrderMapper.updateReturningIdempotentInitialCaptureRequest(606L, futureTimeLocal)
        assertNull(notUpdated4)
        
        val stillCaptured = paymentOrderMapper.findByPaymentOrderId(606L).first()
        assertEquals(PaymentOrderStatus.CAPTURED, stillCaptured.status)
        // Normalize to microsecond precision for comparison (PostgreSQL precision)
        assertEquals(baseTime.normalizeToMicroseconds(), stillCaptured.updatedAt.normalizeToMicroseconds()) // updated_at should remain unchanged
        
        // Test 7: Does not update when status is PENDING_CAPTURE
        val pendingCapture = paymentOrderEntity(
            id = 607L,
            paymentId = 5001L,
            status = PaymentOrderStatus.PENDING_CAPTURE,
            retryCount = 0,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        paymentOrderMapper.insert(pendingCapture)
        
        val notUpdated5 = paymentOrderMapper.updateReturningIdempotentInitialCaptureRequest(607L, futureTimeLocal)
        assertNull(notUpdated5)
        
        val stillPendingCapture = paymentOrderMapper.findByPaymentOrderId(607L).first()
        assertEquals(PaymentOrderStatus.PENDING_CAPTURE, stillPendingCapture.status)
        // Normalize to microsecond precision for comparison (PostgreSQL precision)
        assertEquals(baseTime.normalizeToMicroseconds(), stillPendingCapture.updatedAt.normalizeToMicroseconds()) // updated_at should remain unchanged
    }
}

