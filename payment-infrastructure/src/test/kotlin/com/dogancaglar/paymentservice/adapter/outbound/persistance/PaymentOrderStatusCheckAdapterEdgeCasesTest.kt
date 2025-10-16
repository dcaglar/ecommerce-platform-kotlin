package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentOrderStatusCheckMapper
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatusCheck
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Edge cases and error handling tests for PaymentOrderStatusCheckAdapter.
 * 
 * Tests verify:
 * - Large numbers and boundary values
 * - Future and past dates
 * - Error scenarios
 * - Multiple operations
 */
class PaymentOrderStatusCheckAdapterEdgeCasesTest {

    private lateinit var mapper: PaymentOrderStatusCheckMapper
    private lateinit var adapter: PaymentOrderStatusCheckAdapter

    @BeforeEach
    fun setUp() {
        mapper = mockk(relaxed = true)
        adapter = PaymentOrderStatusCheckAdapter(mapper)
    }

    @Test
    fun `should handle status check with large attempt number`() {
        // Given
        val largeAttempt = 1000
        val statusCheck = createTestPaymentOrderStatusCheck(attempt = largeAttempt)
        every { mapper.insert(any()) } returns 1

        // When
        adapter.save(statusCheck)

        // Then
        verify(exactly = 1) { 
            mapper.insert(
                match { entity -> entity.attempt == largeAttempt }
            )
        }
    }

    @Test
    fun `should handle status check with future scheduled time`() {
        // Given
        val futureTime = LocalDateTime.now().plusDays(1)
        val statusCheck = createTestPaymentOrderStatusCheck(scheduledAt = futureTime)
        every { mapper.insert(any()) } returns 1

        // When
        adapter.save(statusCheck)

        // Then
        verify(exactly = 1) { 
            mapper.insert(
                match { entity -> entity.scheduledAt == futureTime }
            )
        }
    }

    @Test
    fun `should handle status check with past scheduled time`() {
        // Given
        val pastTime = LocalDateTime.of(2020, 1, 1, 0, 0, 0)
        val statusCheck = createTestPaymentOrderStatusCheck(scheduledAt = pastTime)
        every { mapper.insert(any()) } returns 1

        // When
        adapter.save(statusCheck)

        // Then
        verify(exactly = 1) { 
            mapper.insert(
                match { entity -> entity.scheduledAt == pastTime }
            )
        }
    }

    @Test
    fun `should handle large payment order IDs`() {
        // Given
        val largeId = Long.MAX_VALUE
        val statusCheck = createTestPaymentOrderStatusCheck(paymentOrderId = largeId)
        every { mapper.insert(any()) } returns 1

        // When
        adapter.save(statusCheck)

        // Then
        verify(exactly = 1) { 
            mapper.insert(
                match { entity -> entity.paymentOrderId == largeId }
            )
        }
    }

    @Test
    fun `should handle multiple save operations`() {
        // Given
        val statusChecks = (1L..5L).map { createTestPaymentOrderStatusCheck(paymentOrderId = it) }
        every { mapper.insert(any()) } returns 1

        // When
        statusChecks.forEach { adapter.save(it) }

        // Then
        verify(exactly = 5) { mapper.insert(any()) }
    }

    @Test
    fun `should handle multiple markAsProcessed operations`() {
        // Given
        val ids = listOf(1L, 2L, 3L, 4L, 5L)
        every { mapper.markAsProcessed(any(), any()) } just Runs

        // When
        ids.forEach { adapter.markAsProcessed(it) }

        // Then
        verify(exactly = 5) { mapper.markAsProcessed(any(), any()) }
    }

    // ==================== Helper Methods ====================

    private fun createTestPaymentOrderStatusCheck(
        paymentOrderId: Long = 123L,
        scheduledAt: LocalDateTime = LocalDateTime.now().plusMinutes(5),
        attempt: Int = 1,
        status: PaymentOrderStatusCheck.Status = PaymentOrderStatusCheck.Status.SCHEDULED
    ): PaymentOrderStatusCheck {
        return PaymentOrderStatusCheck.reconstructFromPersistence(
            id = 0L,
            paymentOrderId = paymentOrderId,
            scheduledAt = scheduledAt,
            attempt = attempt,
            status = status,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
}
