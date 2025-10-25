package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentOrderEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentOrderMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/**
 * Unit tests for PaymentOrderOutboundAdapter using MockK.
 * 
 * Tests verify:
 * - Domain ↔ Entity mapping logic
 * - updateReturningIdempotent logic
 * - All repository methods delegate correctly to mapper
 * - Error handling and edge cases
 */
class PaymentOrderOutboundAdapterTest {

    private lateinit var paymentOrderMapper: PaymentOrderMapper
    private lateinit var adapter: PaymentOrderOutboundAdapter

    @BeforeEach
    fun setUp() {
        paymentOrderMapper = mockk(relaxed = true)
        adapter = PaymentOrderOutboundAdapter(paymentOrderMapper)
    }

    // ==================== updateReturningIdempotent Tests ====================

    @Test
    fun `updateReturningIdempotent should return updated order when update succeeds`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        val updatedEntity = createTestPaymentOrderEntity(paymentOrder.paymentOrderId.value)
        every { paymentOrderMapper.updateReturningIdempotent(any()) } returns updatedEntity

        // When
        val result = adapter.updateReturningIdempotent(paymentOrder)

        // Then
        assertNotNull(result)
        assertEquals(paymentOrder.paymentOrderId, result!!.paymentOrderId)
        assertEquals(paymentOrder.publicPaymentOrderId, result.publicPaymentOrderId)
        verify(exactly = 1) { paymentOrderMapper.updateReturningIdempotent(any()) }
        verify(exactly = 0) { paymentOrderMapper.findByPaymentOrderId(any()) }
    }

    @Test
    fun `updateReturningIdempotent should return existing order when update fails but order exists`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        val existingEntity = createTestPaymentOrderEntity(paymentOrder.paymentOrderId.value)
        every { paymentOrderMapper.updateReturningIdempotent(any()) } returns null
        every { paymentOrderMapper.findByPaymentOrderId(paymentOrder.paymentOrderId.value) } returns listOf(existingEntity)

        // When
        val result = adapter.updateReturningIdempotent(paymentOrder)

        // Then
        assertNotNull(result)
        assertEquals(paymentOrder.paymentOrderId, result!!.paymentOrderId)
        verify(exactly = 1) { paymentOrderMapper.updateReturningIdempotent(any()) }
        verify(exactly = 1) { paymentOrderMapper.findByPaymentOrderId(paymentOrder.paymentOrderId.value) }
    }

    @Test
    fun `updateReturningIdempotent should return null when update fails and order does not exist`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        every { paymentOrderMapper.updateReturningIdempotent(any()) } returns null
        every { paymentOrderMapper.findByPaymentOrderId(paymentOrder.paymentOrderId.value) } returns emptyList()

        // When
        val result = adapter.updateReturningIdempotent(paymentOrder)

        // Then
        assertNull(result)
        verify(exactly = 1) { paymentOrderMapper.updateReturningIdempotent(any()) }
        verify(exactly = 1) { paymentOrderMapper.findByPaymentOrderId(paymentOrder.paymentOrderId.value) }
    }

    @Test
    fun `updateReturningIdempotent should handle mapper exceptions`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        every { paymentOrderMapper.updateReturningIdempotent(any()) } throws RuntimeException("Database error")

        // When/Then
        assertThrows<RuntimeException> {
            adapter.updateReturningIdempotent(paymentOrder)
        }
    }

    // ==================== insertAll Tests ====================

    @Test
    fun `insertAll should convert domain objects to entities and call mapper`() {
        // Given
        val paymentOrders = listOf(
            createTestPaymentOrder(id = 1L),
            createTestPaymentOrder(id = 2L),
            createTestPaymentOrder(id = 3L)
        )
        every { paymentOrderMapper.insertAllIgnore(any()) } returns 1

        // When
        adapter.insertAll(paymentOrders)

        // Then
        verify(exactly = 1) { 
            paymentOrderMapper.insertAllIgnore(
                match { entities ->
                    entities.size == 3 &&
                    entities[0].paymentOrderId == 1L &&
                    entities[1].paymentOrderId == 2L &&
                    entities[2].paymentOrderId == 3L
                }
            )
        }
    }

    @Test
    fun `insertAll should handle empty list`() {
        // Given
        val emptyList = emptyList<PaymentOrder>()
        every { paymentOrderMapper.insertAllIgnore(any()) } returns 1

        // When
        adapter.insertAll(emptyList)

        // Then
        verify(exactly = 1) { 
            paymentOrderMapper.insertAllIgnore(
                match { entities -> entities.isEmpty() }
            )
        }
    }

    // ==================== countByPaymentId Tests ====================

    @Test
    fun `countByPaymentId should delegate to mapper`() {
        // Given
        val paymentId = PaymentId(123L)
        every { paymentOrderMapper.countByPaymentId(123L) } returns 5L

        // When
        val count = adapter.countByPaymentId(paymentId)

        // Then
        assertEquals(5L, count)
        verify(exactly = 1) { paymentOrderMapper.countByPaymentId(123L) }
    }

    @Test
    fun `countByPaymentId should return zero when no orders found`() {
        // Given
        val paymentId = PaymentId(456L)
        every { paymentOrderMapper.countByPaymentId(456L) } returns 0L

        // When
        val count = adapter.countByPaymentId(paymentId)

        // Then
        assertEquals(0L, count)
    }

    // ==================== findByPaymentOrderId Tests ====================

    @Test
    fun `findByPaymentOrderId should convert entities to domain objects`() {
        // Given
        val paymentOrderId = PaymentOrderId(789L)
        val entities = listOf(
            createTestPaymentOrderEntity(789L),
            createTestPaymentOrderEntity(789L, status = PaymentOrderStatus.SUCCESSFUL_FINAL)
        )
        every { paymentOrderMapper.findByPaymentOrderId(789L) } returns entities

        // When
        val result = adapter.findByPaymentOrderId(paymentOrderId)

        // Then
        assertEquals(2, result.size)
        assertEquals(paymentOrderId, result[0].paymentOrderId)
        assertEquals(paymentOrderId, result[1].paymentOrderId)
        assertEquals(PaymentOrderStatus.INITIATED_PENDING, result[0].status)
        assertEquals(PaymentOrderStatus.SUCCESSFUL_FINAL, result[1].status)
        verify(exactly = 1) { paymentOrderMapper.findByPaymentOrderId(789L) }
    }

    @Test
    fun `findByPaymentOrderId should return empty list when no orders found`() {
        // Given
        val paymentOrderId = PaymentOrderId(999L)
        every { paymentOrderMapper.findByPaymentOrderId(999L) } returns emptyList()

        // When
        val result = adapter.findByPaymentOrderId(paymentOrderId)

        // Then
        assertTrue(result.isEmpty())
    }

    // ==================== countByPaymentIdAndStatusIn Tests ====================

    @Test
    fun `countByPaymentIdAndStatusIn should delegate to mapper with correct parameters`() {
        // Given
        val paymentId = PaymentId(111L)
        val statuses = listOf("INITIATED_PENDING", "SUCCESSFUL_FINAL")
        every { paymentOrderMapper.countByPaymentIdAndStatusIn(111L, statuses) } returns 3L

        // When
        val count = adapter.countByPaymentIdAndStatusIn(paymentId, statuses)

        // Then
        assertEquals(3L, count)
        verify(exactly = 1) { paymentOrderMapper.countByPaymentIdAndStatusIn(111L, statuses) }
    }

    @Test
    fun `countByPaymentIdAndStatusIn should handle empty status list`() {
        // Given
        val paymentId = PaymentId(222L)
        val emptyStatuses = emptyList<String>()
        every { paymentOrderMapper.countByPaymentIdAndStatusIn(222L, emptyStatuses) } returns 0L

        // When
        val count = adapter.countByPaymentIdAndStatusIn(paymentId, emptyStatuses)

        // Then
        assertEquals(0L, count)
    }

    // ==================== existsByPaymentIdAndStatus Tests ====================

    @Test
    fun `existsByPaymentIdAndStatus should return true when order exists`() {
        // Given
        val paymentId = PaymentId(333L)
        val status = "INITIATED_PENDING"
        every { paymentOrderMapper.existsByPaymentIdAndStatus(333L, status) } returns true

        // When
        val exists = adapter.existsByPaymentIdAndStatus(paymentId, status)

        // Then
        assertTrue(exists)
        verify(exactly = 1) { paymentOrderMapper.existsByPaymentIdAndStatus(333L, status) }
    }

    @Test
    fun `existsByPaymentIdAndStatus should return false when order does not exist`() {
        // Given
        val paymentId = PaymentId(444L)
        val status = "FAILED_FINAL"
        every { paymentOrderMapper.existsByPaymentIdAndStatus(444L, status) } returns false

        // When
        val exists = adapter.existsByPaymentIdAndStatus(paymentId, status)

        // Then
        assertFalse(exists)
    }

    // ==================== getMaxPaymentOrderId Tests ====================

    @Test
    fun `getMaxPaymentOrderId should return max ID from mapper`() {
        // Given
        every { paymentOrderMapper.getMaxPaymentOrderId() } returns 1000L

        // When
        val maxId = adapter.getMaxPaymentOrderId()

        // Then
        assertEquals(PaymentOrderId(1000L), maxId)
        verify(exactly = 1) { paymentOrderMapper.getMaxPaymentOrderId() }
    }

    @Test
    fun `getMaxPaymentOrderId should return zero when mapper returns null`() {
        // Given
        every { paymentOrderMapper.getMaxPaymentOrderId() } returns null

        // When
        val maxId = adapter.getMaxPaymentOrderId()

        // Then
        assertEquals(PaymentOrderId(0L), maxId)
    }

    // ==================== Domain ↔ Entity Mapping Tests ====================

    @Test
    fun `should correctly map domain object to entity for updateReturningIdempotent`() {
        // Given
        val paymentOrder = createTestPaymentOrderWithAllFields()
        every { paymentOrderMapper.updateReturningIdempotent(any()) } returns null
        every { paymentOrderMapper.findByPaymentOrderId(any()) } returns emptyList()

        // When
        adapter.updateReturningIdempotent(paymentOrder)

        // Then
        verify(exactly = 1) { 
            paymentOrderMapper.updateReturningIdempotent(
                match { entity ->
                    entity.paymentOrderId == paymentOrder.paymentOrderId.value &&
                    entity.publicPaymentOrderId == paymentOrder.publicPaymentOrderId &&
                    entity.paymentId == paymentOrder.paymentId.value &&
                    entity.publicPaymentId == paymentOrder.publicPaymentId &&
                    entity.sellerId == paymentOrder.sellerId.value &&
                    entity.amountValue == paymentOrder.amount.value &&
                    entity.amountCurrency == paymentOrder.amount.currency &&
                    entity.status == paymentOrder.status &&
                    entity.retryCount == paymentOrder.retryCount &&
                    entity.retryReason == paymentOrder.retryReason &&
                    entity.lastErrorMessage == paymentOrder.lastErrorMessage
                }
            )
        }
    }

    @Test
    fun `should correctly map entity to domain object for findByPaymentOrderId`() {
        // Given
        val entity = createTestPaymentOrderEntityWithAllFields()
        every { paymentOrderMapper.findByPaymentOrderId(any()) } returns listOf(entity)

        // When
        val result = adapter.findByPaymentOrderId(PaymentOrderId(entity.paymentOrderId))

        // Then
        assertEquals(1, result.size)
        val domainObject = result[0]
        assertEquals(entity.paymentOrderId, domainObject.paymentOrderId.value)
        assertEquals(entity.publicPaymentOrderId, domainObject.publicPaymentOrderId)
        assertEquals(entity.paymentId, domainObject.paymentId.value)
        assertEquals(entity.publicPaymentId, domainObject.publicPaymentId)
        assertEquals(entity.sellerId, domainObject.sellerId.value)
        assertEquals(entity.amountValue, domainObject.amount.value)
        assertEquals(entity.amountCurrency, domainObject.amount.currency)
        assertEquals(entity.status, domainObject.status)
        assertEquals(entity.retryCount, domainObject.retryCount)
        assertEquals(entity.retryReason, domainObject.retryReason)
        assertEquals(entity.lastErrorMessage, domainObject.lastErrorMessage)
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    fun `should handle large lists in insertAll`() {
        // Given
        val largeList = (1L..1000L).map { createTestPaymentOrder(id = it) }
        every { paymentOrderMapper.insertAllIgnore(any()) } returns 1

        // When
        adapter.insertAll(largeList)

        // Then
        verify(exactly = 1) { 
            paymentOrderMapper.insertAllIgnore(
                match { entities -> entities.size == 1000 }
            )
        }
    }

    @Test
    fun `should handle null values in optional fields`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(1L))
            .publicPaymentOrderId("test_order_1")
            .paymentId(PaymentId(1L))
            .publicPaymentId("payment-1")
            .sellerId(SellerId("seller_1"))
            .amount(Amount(10000L, "USD"))
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()
        every { paymentOrderMapper.updateReturningIdempotent(any()) } returns null
        every { paymentOrderMapper.findByPaymentOrderId(any()) } returns emptyList()

        // When
        adapter.updateReturningIdempotent(paymentOrder)

        // Then
        verify(exactly = 1) { 
            paymentOrderMapper.updateReturningIdempotent(
                match { entity ->
                    entity.retryReason == null &&
                    entity.lastErrorMessage == null
                }
            )
        }
    }

    // ==================== Helper Methods ====================

    private fun createTestPaymentOrder(
        id: Long = 123L,
        retryCount: Int = 0,
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING
    ): PaymentOrder {
        return PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(id))
            .publicPaymentOrderId("po-$id")
            .paymentId(PaymentId(999L))
            .publicPaymentId("pay-999")
            .sellerId(SellerId("111"))
            .amount(Amount(10000L, "USD"))
            .status(status)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .retryCount(retryCount)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()
    }

    private fun createTestPaymentOrderWithAllFields(): PaymentOrder {
        return PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(456L))
            .publicPaymentOrderId("po-456")
            .paymentId(PaymentId(888L))
            .publicPaymentId("pay-888")
            .sellerId(SellerId("seller-456"))
            .amount(Amount(25000L, "EUR"))
            .status(PaymentOrderStatus.FAILED_TRANSIENT_ERROR)
            .createdAt(LocalDateTime.of(2023, 6, 15, 10, 30))
            .updatedAt(LocalDateTime.of(2023, 6, 15, 10, 35))
            .retryCount(2)
            .retryReason("PSP_TIMEOUT")
            .lastErrorMessage("Connection timeout after 30 seconds")
            .buildFromPersistence()
    }

    private fun createTestPaymentOrderEntity(
        id: Long,
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING
    ): PaymentOrderEntity {
        return PaymentOrderEntity(
            paymentOrderId = id,
            publicPaymentOrderId = "po-$id",
            paymentId = 999L,
            publicPaymentId = "pay-999",
            sellerId = "111",
            amountValue = 10000L,
            amountCurrency = "USD",
            status = status,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            retryCount = 0
        )
    }

    private fun createTestPaymentOrderEntityWithAllFields(): PaymentOrderEntity {
        return PaymentOrderEntity(
            paymentOrderId = 456L,
            publicPaymentOrderId = "po-456",
            paymentId = 888L,
            publicPaymentId = "pay-888",
            sellerId = "seller-456",
            amountValue = 25000L,
            amountCurrency = "EUR",
            status = PaymentOrderStatus.FAILED_TRANSIENT_ERROR,
            createdAt = LocalDateTime.of(2023, 6, 15, 10, 30),
            updatedAt = LocalDateTime.of(2023, 6, 15, 10, 35),
            retryCount = 2,
            retryReason = "PSP_TIMEOUT",
            lastErrorMessage = "Connection timeout after 30 seconds"
        )
    }
}
