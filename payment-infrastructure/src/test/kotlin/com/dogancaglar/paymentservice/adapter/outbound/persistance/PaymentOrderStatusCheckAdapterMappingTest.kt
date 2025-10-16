package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentOrderStatusCheckEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentOrderStatusCheckMapper
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatusCheck
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Mapping and data conversion tests for PaymentOrderStatusCheckAdapter.
 * 
 * Tests verify:
 * - Domain to Entity mapping
 * - Entity to Domain mapping
 * - Complex data scenarios
 * - Field mapping accuracy
 */
class PaymentOrderStatusCheckAdapterMappingTest {

    private lateinit var mapper: PaymentOrderStatusCheckMapper
    private lateinit var adapter: PaymentOrderStatusCheckAdapter

    @BeforeEach
    fun setUp() {
        mapper = mockk(relaxed = true)
        adapter = PaymentOrderStatusCheckAdapter(mapper)
    }

    @Test
    fun `should correctly map domain object to entity for save`() {
        // Given
        val statusCheck = createTestPaymentOrderStatusCheckWithComplexData()
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
                    entity.createdAt == statusCheck.createdAt &&
                    entity.updatedAt == statusCheck.updatedAt
                }
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

    @Test
    fun `should handle multiple entities in findDueStatusChecks`() {
        // Given
        val now = LocalDateTime.of(2023, 6, 15, 10, 30)
        val pastTime1 = now.minusMinutes(5)
        val pastTime2 = now.minusMinutes(10)
        val pastTime3 = now.minusMinutes(15)
        
        val entities = listOf(
            createTestPaymentOrderStatusCheckEntity(1L, pastTime1),
            createTestPaymentOrderStatusCheckEntity(2L, pastTime2),
            createTestPaymentOrderStatusCheckEntity(3L, pastTime3)
        )
        every { mapper.findDue(now) } returns entities

        // When
        val result = adapter.findDueStatusChecks(now)

        // Then
        assertEquals(3, result.size)
        assertEquals(1L, result[0].paymentOrderId)
        assertEquals(2L, result[1].paymentOrderId)
        assertEquals(3L, result[2].paymentOrderId)
        assertEquals(pastTime1, result[0].scheduledAt)
        assertEquals(pastTime2, result[1].scheduledAt)
        assertEquals(pastTime3, result[2].scheduledAt)
    }

    @Test
    fun `should preserve all field values during domain to entity mapping`() {
        // Given
        val statusCheck = PaymentOrderStatusCheck.reconstructFromPersistence(
            id = 999L,
            paymentOrderId = 888L,
            scheduledAt = LocalDateTime.of(2023, 12, 31, 23, 59, 59),
            attempt = 42,
            status = PaymentOrderStatusCheck.Status.PROCESSED,
            createdAt = LocalDateTime.of(2023, 1, 1, 0, 0, 0),
            updatedAt = LocalDateTime.of(2023, 12, 31, 23, 59, 59)
        )
        every { mapper.insert(any()) } returns 1

        // When
        adapter.save(statusCheck)

        // Then
        verify(exactly = 1) { 
            mapper.insert(
                match { entity ->
                    entity.paymentOrderId == 888L &&
                    entity.scheduledAt == LocalDateTime.of(2023, 12, 31, 23, 59, 59) &&
                    entity.attempt == 42 &&
                    entity.status == "PROCESSED" &&
                    entity.createdAt == LocalDateTime.of(2023, 1, 1, 0, 0, 0) &&
                    entity.updatedAt == LocalDateTime.of(2023, 12, 31, 23, 59, 59)
                }
            )
        }
    }

    // ==================== Helper Methods ====================

    private fun createTestPaymentOrderStatusCheckWithComplexData(): PaymentOrderStatusCheck {
        return PaymentOrderStatusCheck.reconstructFromPersistence(
            id = 789L,
            paymentOrderId = 789L,
            scheduledAt = LocalDateTime.of(2023, 12, 31, 23, 59, 59),
            attempt = 5,
            status = PaymentOrderStatusCheck.Status.PROCESSED,
            createdAt = LocalDateTime.of(2023, 1, 1, 0, 0, 0),
            updatedAt = LocalDateTime.of(2023, 12, 31, 23, 59, 59)
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
}
