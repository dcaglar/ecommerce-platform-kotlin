package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentOrderEntity
import com.dogancaglar.paymentservice.domain.event.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Unit tests for entity mappers.
 * 
 * Tests verify:
 * - Domain ↔ Entity mapping logic for all mappers
 * - Bidirectional mapping (toEntity and toDomain)
 * - Edge cases and special values
 * - Null handling
 */
class EntityMapperTest {

    // ==================== PaymentOrderEntityMapper Tests ====================

    @Test
    fun `PaymentOrderEntityMapper toEntity should map all fields correctly`() {
        // Given
        val paymentOrder = createTestPaymentOrder()

        // When
        val entity = PaymentOrderEntityMapper.toEntity(paymentOrder)

        // Then
        assertEquals(paymentOrder.paymentOrderId.value, entity.paymentOrderId)
        assertEquals(paymentOrder.publicPaymentOrderId, entity.publicPaymentOrderId)
        assertEquals(paymentOrder.paymentId.value, entity.paymentId)
        assertEquals(paymentOrder.publicPaymentId, entity.publicPaymentId)
        assertEquals(paymentOrder.sellerId.value, entity.sellerId)
        assertEquals(paymentOrder.amount.value, entity.amountValue)
        assertEquals(paymentOrder.amount.currency.currencyCode, entity.amountCurrency)
        assertEquals(paymentOrder.status, entity.status)
        assertEquals(paymentOrder.createdAt, entity.createdAt)
        assertEquals(paymentOrder.updatedAt, entity.updatedAt)
        assertEquals(paymentOrder.retryCount, entity.retryCount)
        assertEquals(paymentOrder.retryReason, entity.retryReason)
        assertEquals(paymentOrder.lastErrorMessage, entity.lastErrorMessage)
    }

    @Test
    fun `PaymentOrderEntityMapper toDomain should map all fields correctly`() {
        // Given
        val entity = createTestPaymentOrderEntity()

        // When
        val domain = PaymentOrderEntityMapper.toDomain(entity)

        // Then
        assertEquals(entity.paymentOrderId, domain.paymentOrderId.value)
        assertEquals(entity.publicPaymentOrderId, domain.publicPaymentOrderId)
        assertEquals(entity.paymentId, domain.paymentId.value)
        assertEquals(entity.publicPaymentId, domain.publicPaymentId)
        assertEquals(entity.sellerId, domain.sellerId.value)
        assertEquals(entity.amountValue, domain.amount.value)
        assertEquals(entity.amountCurrency, domain.amount.currency.currencyCode)
        assertEquals(entity.status, domain.status)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(entity.updatedAt, domain.updatedAt)
        assertEquals(entity.retryCount, domain.retryCount)
        assertEquals(entity.retryReason, domain.retryReason)
        assertEquals(entity.lastErrorMessage, domain.lastErrorMessage)
    }

    @Test
    fun `PaymentOrderEntityMapper should handle bidirectional mapping`() {
        // Given
        val originalPaymentOrder = createTestPaymentOrderWithAllFields()

        // When
        val entity = PaymentOrderEntityMapper.toEntity(originalPaymentOrder)
        val restoredPaymentOrder = PaymentOrderEntityMapper.toDomain(entity)

        // Then
        assertEquals(originalPaymentOrder.paymentOrderId, restoredPaymentOrder.paymentOrderId)
        assertEquals(originalPaymentOrder.publicPaymentOrderId, restoredPaymentOrder.publicPaymentOrderId)
        assertEquals(originalPaymentOrder.paymentId, restoredPaymentOrder.paymentId)
        assertEquals(originalPaymentOrder.publicPaymentId, restoredPaymentOrder.publicPaymentId)
        assertEquals(originalPaymentOrder.sellerId, restoredPaymentOrder.sellerId)
        assertEquals(originalPaymentOrder.amount.value, restoredPaymentOrder.amount.value)
        assertEquals(originalPaymentOrder.amount.currency, restoredPaymentOrder.amount.currency)
        assertEquals(originalPaymentOrder.status, restoredPaymentOrder.status)
        assertEquals(originalPaymentOrder.createdAt, restoredPaymentOrder.createdAt)
        assertEquals(originalPaymentOrder.updatedAt, restoredPaymentOrder.updatedAt)
        assertEquals(originalPaymentOrder.retryCount, restoredPaymentOrder.retryCount)
        assertEquals(originalPaymentOrder.retryReason, restoredPaymentOrder.retryReason)
        assertEquals(originalPaymentOrder.lastErrorMessage, restoredPaymentOrder.lastErrorMessage)
    }

    @Test
    fun `PaymentOrderEntityMapper should handle null values`() {
        // Given
        val paymentOrder = createTestPaymentOrder().copy(
            retryReason = null,
            lastErrorMessage = null
        )

        // When
        val entity = PaymentOrderEntityMapper.toEntity(paymentOrder)
        val restored = PaymentOrderEntityMapper.toDomain(entity)

        // Then
        assertNull(entity.retryReason)
        assertNull(entity.lastErrorMessage)
        assertNull(restored.retryReason)
        assertNull(restored.lastErrorMessage)
    }

    // ==================== PaymentEntityMapper Tests ====================

    @Test
    fun `PaymentEntityMapper toEntity should map all fields correctly`() {
        // Given
        val payment = createTestPayment()

        // When
        val entity = PaymentEntityMapper.toEntity(payment)

        // Then
        assertEquals(payment.paymentId.value, entity.paymentId)
        assertEquals(payment.publicPaymentId, entity.publicPaymentId)
        assertEquals(payment.buyerId.value, entity.buyerId)
        assertEquals(payment.orderId.value, entity.orderId)
        assertEquals(payment.totalAmount.value, entity.amountValue)
        assertEquals(payment.totalAmount.currency.currencyCode, entity.amountCurrency)
        assertEquals(payment.status, entity.status)
        assertEquals(payment.createdAt, entity.createdAt)
    }

    @Test
    fun `PaymentEntityMapper should handle different payment statuses`() {
        // Given
        val statuses = listOf(
            PaymentStatus.INITIATED,
            PaymentStatus.SUCCESS,
            PaymentStatus.FAILED
        )

        // When/Then
        statuses.forEach { status ->
            val payment = createTestPayment(status = status)
            val entity = PaymentEntityMapper.toEntity(payment)
            assertEquals(status, entity.status)
        }
    }

    @Test
    fun `PaymentEntityMapper should handle different currencies`() {
        // Given
        val currencies = listOf("USD", "EUR", "GBP", "JPY", "CAD")

        // When/Then
        currencies.forEach { currency ->
            val payment = Payment.Builder()
                .paymentId(PaymentId(123L))
                .publicPaymentId("pay-123")
                .buyerId(BuyerId("buyer-123"))
                .orderId(OrderId("order-123"))
                .totalAmount(Amount.of(10000L, Currency(currency)))
                .status(PaymentStatus.INITIATED)
                .createdAt(LocalDateTime.now())
                .paymentOrders(emptyList())
                .build()
            val entity = PaymentEntityMapper.toEntity(payment)
            assertEquals(currency, entity.amountCurrency)
            assertEquals(10000L, entity.amountValue)
        }
    }

    @Test
    fun `PaymentEntityMapper should handle special characters in IDs`() {
        // Given
        val payment = Payment.Builder()
            .paymentId(PaymentId(123L))
            .publicPaymentId("pay-123-test_@#$%")
            .buyerId(BuyerId("buyer-456-special!@#"))
            .orderId(OrderId("order-789-unicode-测试"))
            .totalAmount(Amount.of(10000L, Currency("USD")))
            .status(PaymentStatus.INITIATED)
            .createdAt(LocalDateTime.now())
            .paymentOrders(emptyList())
            .build()

        // When
        val entity = PaymentEntityMapper.toEntity(payment)

        // Then
        assertEquals("pay-123-test_@#$%", entity.publicPaymentId)
        assertEquals("buyer-456-special!@#", entity.buyerId)
        assertEquals("order-789-unicode-测试", entity.orderId)
    }

    // ==================== OutboxEventEntityMapper Tests ====================

    @Test
    fun `OutboxEventEntityMapper toEntity should map all fields correctly`() {
        // Given
        val outboxEvent = createTestOutboxEvent()

        // When
        val entity = OutboxEventEntityMapper.toEntity(outboxEvent)

        // Then
        assertEquals(outboxEvent.oeid, entity.oeid)
        assertEquals(outboxEvent.eventType, entity.eventType)
        assertEquals(outboxEvent.aggregateId, entity.aggregateId)
        assertEquals(outboxEvent.payload, entity.payload)
        assertEquals(outboxEvent.status.toString(), entity.status)
        assertEquals(outboxEvent.createdAt, entity.createdAt)
    }

    @Test
    fun `OutboxEventEntityMapper toDomain should map all fields correctly`() {
        // Given
        val entity = createTestOutboxEventEntity()

        // When
        val domain = OutboxEventEntityMapper.toDomain(entity)

        // Then
        assertEquals(entity.oeid, domain.oeid)
        assertEquals(entity.eventType, domain.eventType)
        assertEquals(entity.aggregateId, domain.aggregateId)
        assertEquals(entity.payload, domain.payload)
        assertEquals(entity.status, domain.status.toString())
        assertEquals(entity.createdAt, domain.createdAt)
    }

    @Test
    fun `OutboxEventEntityMapper should handle bidirectional mapping`() {
        // Given
        val originalEvent = createTestOutboxEventWithAllFields()

        // When
        val entity = OutboxEventEntityMapper.toEntity(originalEvent)
        val restoredEvent = OutboxEventEntityMapper.toDomain(entity)

        // Then
        assertEquals(originalEvent.oeid, restoredEvent.oeid)
        assertEquals(originalEvent.eventType, restoredEvent.eventType)
        assertEquals(originalEvent.aggregateId, restoredEvent.aggregateId)
        assertEquals(originalEvent.payload, restoredEvent.payload)
        assertEquals(originalEvent.status, restoredEvent.status)
        assertEquals(originalEvent.createdAt, restoredEvent.createdAt)
    }

    @Test
    fun `OutboxEventEntityMapper should handle different statuses`() {
        // Given
        val statuses = listOf("NEW", "PROCESSING", "SENT")

        // When/Then
        statuses.forEach { status ->
            val event = createTestOutboxEvent().copy(status = status)
            val entity = OutboxEventEntityMapper.toEntity(event)
            assertEquals(status, entity.status)
        }
    }

    @Test
    fun `OutboxEventEntityMapper should handle special characters in payload`() {
        // Given
        val specialPayload = """{"message": "Test with special chars: \"quotes\", 'apostrophes', \n newlines, \t tabs, \\ backslashes"}"""
        val event = createTestOutboxEvent().copy(payload = specialPayload)

        // When
        val entity = OutboxEventEntityMapper.toEntity(event)
        val restored = OutboxEventEntityMapper.toDomain(entity)

        // Then
        assertEquals(specialPayload, entity.payload)
        assertEquals(specialPayload, restored.payload)
    }

    @Test
    fun `OutboxEventEntityMapper should handle unicode characters`() {
        // Given
        val unicodePayload = """{"message": "Test with unicode: 中文, العربية, русский, 🚀 emoji"}"""
        val event = createTestOutboxEvent().copy(
            payload = unicodePayload,
            aggregateId = "aggregate-测试-123"
        )

        // When
        val entity = OutboxEventEntityMapper.toEntity(event)
        val restored = OutboxEventEntityMapper.toDomain(entity)

        // Then
        assertEquals(unicodePayload, entity.payload)
        assertEquals("aggregate-测试-123", entity.aggregateId)
        assertEquals(unicodePayload, restored.payload)
        assertEquals("aggregate-测试-123", restored.aggregateId)
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    fun `all mappers should handle zero values`() {
        // Given
        val zeroPaymentOrder = createTestPaymentOrder().copy(
            retryCount = 0
        )
        val zeroPayment = createTestPayment().copy(
            totalAmount = Amount.of(0L, Currency("USD"))
        )
        val zeroOutboxEvent = createTestOutboxEvent().copy(oeid = 0L)

        // When
        val paymentOrderEntity = PaymentOrderEntityMapper.toEntity(zeroPaymentOrder)
        val paymentEntity = PaymentEntityMapper.toEntity(zeroPayment)
        val outboxEventEntity = OutboxEventEntityMapper.toEntity(zeroOutboxEvent)

        // Then
        assertEquals(0, paymentOrderEntity.retryCount)
        assertEquals(0L, paymentEntity.amountValue)
        assertEquals(0L, outboxEventEntity.oeid)
    }

    @Test
    fun `all mappers should handle maximum values`() {
        // Given
        val maxPaymentOrder = createTestPaymentOrder().copy(
            paymentOrderId = PaymentOrderId(Long.MAX_VALUE),
            retryCount = Int.MAX_VALUE
        )
        val maxPayment = createTestPayment().copy(
            paymentId = PaymentId(Long.MAX_VALUE),
            totalAmount = Amount.of(Long.MAX_VALUE, Currency("USD"))
        )
        val maxOutboxEvent = createTestOutboxEvent().copy(oeid = Long.MAX_VALUE)

        // When
        val paymentOrderEntity = PaymentOrderEntityMapper.toEntity(maxPaymentOrder)
        val paymentEntity = PaymentEntityMapper.toEntity(maxPayment)
        val outboxEventEntity = OutboxEventEntityMapper.toEntity(maxOutboxEvent)

        // Then
        assertEquals(Long.MAX_VALUE, paymentOrderEntity.paymentOrderId)
        assertEquals(Int.MAX_VALUE, paymentOrderEntity.retryCount)
        assertEquals(Long.MAX_VALUE, paymentEntity.paymentId)
        assertEquals(Long.MAX_VALUE, paymentEntity.amountValue)
        assertEquals(Long.MAX_VALUE, outboxEventEntity.oeid)
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
            .amount(Amount.of(10000L, Currency("USD")))
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
            .amount(Amount.of(25000L, Currency("EUR")))
            .status(PaymentOrderStatus.FAILED_TRANSIENT_ERROR)
            .createdAt(LocalDateTime.of(2023, 6, 15, 10, 30))
            .updatedAt(LocalDateTime.of(2023, 6, 15, 10, 35))
            .retryCount(2)
            .retryReason("PSP_TIMEOUT")
            .lastErrorMessage("Connection timeout after 30 seconds")
            .buildFromPersistence()
    }

    private fun createTestPaymentOrderEntity(): PaymentOrderEntity {
        return PaymentOrderEntity(
            paymentOrderId = 123L,
            publicPaymentOrderId = "po-123",
            paymentId = 999L,
            publicPaymentId = "pay-999",
            sellerId = "111",
            amountValue = 10000L,
            amountCurrency = "USD",
            status = PaymentOrderStatus.INITIATED_PENDING,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            retryCount = 0
        )
    }

    private fun createTestPayment(
        id: Long = 123L,
        status: PaymentStatus = PaymentStatus.INITIATED
    ): Payment {
        return Payment.Builder()
            .paymentId(PaymentId(id))
            .publicPaymentId("pay-$id")
            .buyerId(BuyerId("buyer-$id"))
            .orderId(OrderId("order-$id"))
            .totalAmount(Amount.of(10000L, Currency("USD")))
            .status(status)
            .createdAt(LocalDateTime.now())
            .paymentOrders(emptyList())
            .build()
    }

    private fun createTestOutboxEvent(
        oeid: Long = 123L,
        status: String = "NEW"
    ): OutboxEvent {
        return OutboxEvent.restore(
            oeid = oeid,
            eventType = "payment_order_created",
            aggregateId = "agg-$oeid",
            payload = """{"paymentOrderId": "$oeid", "amount": 10000}""",
            status = status,
            createdAt = LocalDateTime.now()
        )
    }

    private fun createTestOutboxEventWithAllFields(): OutboxEvent {
        return OutboxEvent.restore(
            oeid = 456L,
            eventType = "payment_order_psp_call_requested",
            aggregateId = "agg-456",
            payload = """{"paymentOrderId": "456", "amount": 25000, "currency": "EUR"}""",
            status = "PROCESSING",
            createdAt = LocalDateTime.of(2023, 6, 15, 10, 30)
        )
    }

    private fun createTestOutboxEventEntity(): OutboxEventEntity {
        return OutboxEventEntity(
            oeid = 123L,
            eventType = "payment_order_created",
            aggregateId = "agg-123",
            payload = """{"paymentOrderId": "123", "amount": 10000}""",
            status = "NEW",
            createdAt = LocalDateTime.now()
        )
    }

    // Extension functions for creating copies with different fields
    private fun PaymentOrder.copy(
        paymentOrderId: PaymentOrderId = this.paymentOrderId,
        retryCount: Int = this.retryCount,
        retryReason: String? = this.retryReason,
        lastErrorMessage: String? = this.lastErrorMessage
    ): PaymentOrder {
        return PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId(this.publicPaymentOrderId)
            .paymentId(this.paymentId)
            .publicPaymentId(this.publicPaymentId)
            .sellerId(this.sellerId)
            .amount(this.amount)
            .status(this.status)
            .createdAt(this.createdAt)
            .updatedAt(this.updatedAt)
            .retryCount(retryCount)
            .retryReason(retryReason)
            .lastErrorMessage(lastErrorMessage)
            .buildFromPersistence()
    }

    private fun Payment.copy(
        paymentId: PaymentId = this.paymentId,
        status: PaymentStatus = this.status,
        totalAmount: Amount = this.totalAmount
    ): Payment {
        return Payment.Builder()
            .paymentId(paymentId)
            .publicPaymentId(this.publicPaymentId)
            .buyerId(this.buyerId)
            .orderId(this.orderId)
            .totalAmount(totalAmount)
            .status(status)
            .createdAt(this.createdAt)
            .paymentOrders(emptyList())
            .build()
    }

    private fun OutboxEvent.copy(
        oeid: Long = this.oeid,
        status: String = this.status.toString(),
        payload: String = this.payload,
        aggregateId: String = this.aggregateId
    ): OutboxEvent {
        return OutboxEvent.restore(
            oeid = oeid,
            eventType = this.eventType,
            aggregateId = aggregateId,
            payload = payload,
            status = status,
            createdAt = this.createdAt
        )
    }
}
