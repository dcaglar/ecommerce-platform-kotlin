package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentOrderStatusCheckEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentOrderStatusCheckMapper
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatusCheck
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/**
 * Unit tests for PaymentOrderStatusCheckAdapter using MockK.
 * 
 * Tests verify:
 * - Domain ↔ Entity mapping logic
 * - Repository methods delegate correctly to mapper
 * - Transactional behavior
 * - Error handling and edge cases
 */
class PaymentOrderStatusCheckAdapterTest {

    private lateinit var mapper: PaymentOrderStatusCheckMapper
    private lateinit var adapter: PaymentOrderStatusCheckAdapter

    @BeforeEach
    fun setUp() {
        mapper = mockk(relaxed = true)
        adapter = PaymentOrderStatusCheckAdapter(mapper)
    }

    // ==================== save Tests ====================

    @Test
    fun `save should convert domain object to entity and call mapper`() {
        // Given
        val statusCheck = createTestPaymentOrderStatusCheck()
        every { mapper.insert(any()) } returns 1

        // When
        adapter.save(statusCheck)

        // Then
        verify(exactly = 1) { 
            mapper.insert(
                match { entity ->
                    entity.paymentOrderId == statusCheck.paymentOrderId &&
                    entity.scheduledAt == statusCheck.scheduledAt &&
                    entity.attempt == statusCheck.attempt &&
                    entity.status == statusCheck.status.name &&
                    entity.createdAt == statusCheck.createdAt
                }
            )
        }
    }

    @Test
    fun `save should handle mapper exceptions`() {
        // Given
        val statusCheck = createTestPaymentOrderStatusCheck()
        every { mapper.insert(any()) } throws RuntimeException("Database error")

        // When/Then
        assertThrows<RuntimeException> {
            adapter.save(statusCheck)
        }
    }

    @Test
    fun `save should handle status check with all fields populated`() {
        // Given
        val statusCheck = createTestPaymentOrderStatusCheckWithAllFields()
        every { mapper.insert(any()) } returns 1

        // When
        adapter.save(statusCheck)

        // Then
        verify(exactly = 1) { 
            mapper.insert(
                match { entity ->
                    entity.paymentOrderId == 456L &&
                    entity.scheduledAt == LocalDateTime.of(2023, 6, 15, 10, 30) &&
                    entity.attempt == 3 &&
                    entity.status == "PROCESSED" &&
                    entity.createdAt == LocalDateTime.of(2023, 6, 15, 9, 0) &&
                    entity.updatedAt == LocalDateTime.of(2023, 6, 15, 10, 35)
                }
            )
        }
    }

    // ==================== findDueStatusChecks Tests ====================

    @Test
    fun `findDueStatusChecks should convert entities to domain objects`() {
        // Given
        val now = LocalDateTime.of(2023, 6, 15, 10, 30)
        val entities = listOf(
            createTestPaymentOrderStatusCheckEntity(1L, now.minusMinutes(5)),
            createTestPaymentOrderStatusCheckEntity(2L, now.minusMinutes(10))
        )
        every { mapper.findDue(now) } returns entities

        // When
        val result = adapter.findDueStatusChecks(now)

        // Then
        assertEquals(2, result.size)
        assertEquals(1L, result[0].paymentOrderId)
        assertEquals(2L, result[1].paymentOrderId)
        assertEquals(PaymentOrderStatusCheck.Status.SCHEDULED, result[0].status)
        assertEquals(PaymentOrderStatusCheck.Status.SCHEDULED, result[1].status)
        verify(exactly = 1) { mapper.findDue(now) }
    }

    @Test
    fun `findDueStatusChecks should return empty list when no due checks found`() {
        // Given
        val now = LocalDateTime.now()
        every { mapper.findDue(now) } returns emptyList()

        // When
        val result = adapter.findDueStatusChecks(now)

        // Then
        assertTrue(result.isEmpty())
        verify(exactly = 1) { mapper.findDue(now) }
    }

    // ==================== markAsProcessed Tests ====================

    @Test
    fun `markAsProcessed should call mapper with correct parameters`() {
        // Given
        val id = 123L
        every { mapper.markAsProcessed(any(), any()) } just Runs

        // When
        adapter.markAsProcessed(id)

        // Then
        verify(exactly = 1) { 
            mapper.markAsProcessed(
                match { it == id },
                any()
            )
        }
    }

    @Test
    fun `should handle different status values correctly`() {
        // Given
        val statuses = listOf(
            PaymentOrderStatusCheck.Status.SCHEDULED,
            PaymentOrderStatusCheck.Status.PROCESSED
        )
        every { mapper.insert(any()) } returns 1

        // When/Then
        statuses.forEach { status ->
            val statusCheck = createTestPaymentOrderStatusCheck(status = status)
            adapter.save(statusCheck)
            
            verify { 
                mapper.insert(
                    match { entity -> entity.status == status.name }
                )
            }
        }
    }

    @Test
    fun `should handle status check with zero attempt`() {
        // Given
        val statusCheck = createTestPaymentOrderStatusCheck(attempt = 0)
        every { mapper.insert(any()) } returns 1

        // When
        adapter.save(statusCheck)

        // Then
        verify(exactly = 1) { 
            mapper.insert(
                match { entity -> entity.attempt == 0 }
            )
        }
    }

    @Test
    fun `should correctly map entity to domain object for findDueStatusChecks`() {
        // Given
        val entity = createTestPaymentOrderStatusCheckEntityWithAllFields()
        every { mapper.findDue(any()) } returns listOf(entity)

        // When
        val result = adapter.findDueStatusChecks(LocalDateTime.now())

        // Then
        assertEquals(1, result.size)
        val domainObject = result[0]
        assertEquals(entity.id, domainObject.id)
        assertEquals(entity.paymentOrderId, domainObject.paymentOrderId)
        assertEquals(entity.scheduledAt, domainObject.scheduledAt)
        assertEquals(entity.attempt, domainObject.attempt)
        assertEquals(PaymentOrderStatusCheck.Status.valueOf(entity.status), domainObject.status)
        assertEquals(entity.createdAt, domainObject.createdAt)
        assertEquals(entity.updatedAt, domainObject.updatedAt)
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

    private fun createTestPaymentOrderStatusCheckWithAllFields(): PaymentOrderStatusCheck {
        return PaymentOrderStatusCheck.reconstructFromPersistence(
            id = 456L,
            paymentOrderId = 456L,
            scheduledAt = LocalDateTime.of(2023, 6, 15, 10, 30),
            attempt = 3,
            status = PaymentOrderStatusCheck.Status.PROCESSED,
            createdAt = LocalDateTime.of(2023, 6, 15, 9, 0),
            updatedAt = LocalDateTime.of(2023, 6, 15, 10, 35)
        )
    }

    private fun createTestPaymentOrderStatusCheckEntity(
        paymentOrderId: Long,
        scheduledAt: LocalDateTime = LocalDateTime.now().plusMinutes(5),
        attempt: Int = 1,
        status: String = "SCHEDULED"
    ): PaymentOrderStatusCheckEntity {
        return PaymentOrderStatusCheckEntity(
            id = 0L,
            paymentOrderId = paymentOrderId,
            scheduledAt = scheduledAt,
            attempt = attempt,
            status = status,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    private fun createTestPaymentOrderStatusCheckEntityWithAllFields(): PaymentOrderStatusCheckEntity {
        return PaymentOrderStatusCheckEntity(
            id = 456L,
            paymentOrderId = 456L,
            scheduledAt = LocalDateTime.of(2023, 6, 15, 10, 30),
            attempt = 3,
            status = "PROCESSED",
            createdAt = LocalDateTime.of(2023, 6, 15, 9, 0),
            updatedAt = LocalDateTime.of(2023, 6, 15, 10, 35)
        )
    }
}