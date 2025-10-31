package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.domain.PaymentOrderStatusCheckRequested
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PaymentOrderDomainEventMapperTest {

    private val clock = java.time.Clock.fixed(java.time.Instant.parse("2024-01-01T12:00:00Z"), java.time.ZoneId.of("UTC"))
    private val now = LocalDateTime.now(clock)
    private val testPaymentOrder = createTestPaymentOrder()
    private val mapper = PaymentOrderDomainEventMapper(clock)

    @Test
    fun `toPaymentOrderPspCallRequested should map all fields correctly`() {
        val attempt = 3
        val event = mapper.toPaymentOrderPspCallRequested(testPaymentOrder, attempt)

        assertEquals(testPaymentOrder.paymentOrderId.value.toString(), event.paymentOrderId)
        assertEquals(testPaymentOrder.publicPaymentOrderId, event.publicPaymentOrderId)
        assertEquals(testPaymentOrder.paymentId.value.toString(), event.paymentId)
        assertEquals(testPaymentOrder.publicPaymentId, event.publicPaymentId)
        assertEquals(testPaymentOrder.sellerId.value, event.sellerId)
        assertEquals(attempt, event.retryCount)
        assertEquals(testPaymentOrder.retryReason, event.retryReason)
        assertEquals(testPaymentOrder.lastErrorMessage, event.lastErrorMessage)
        assertEquals(testPaymentOrder.createdAt, event.createdAt)
        assertEquals(testPaymentOrder.updatedAt, event.updatedAt)
        assertEquals(testPaymentOrder.status.name, event.status)
        assertEquals(testPaymentOrder.amount.value, event.amountValue)
        assertEquals(testPaymentOrder.amount.currency.currencyCode, event.currency)
    }

    @Test
    fun `toPaymentOrderPspCallRequested should use provided attempt count`() {
        val attempt = 5
        val event = mapper.toPaymentOrderPspCallRequested(testPaymentOrder, attempt)

        assertEquals(attempt, event.retryCount)
    }

    @Test
    fun `toPaymentOrderCreatedEvent should map all fields correctly`() {
        val event = mapper.toPaymentOrderCreated(testPaymentOrder)

        assertEquals(testPaymentOrder.paymentOrderId.value.toString(), event.paymentOrderId)
        assertEquals(testPaymentOrder.publicPaymentOrderId, event.publicPaymentOrderId)
        assertEquals(testPaymentOrder.paymentId.value.toString(), event.paymentId)
        assertEquals(testPaymentOrder.publicPaymentId, event.publicPaymentId)
        assertEquals(testPaymentOrder.sellerId.value, event.sellerId)
        assertEquals(testPaymentOrder.retryCount, event.retryCount)
        assertEquals(testPaymentOrder.status.name, event.status)
        assertEquals(testPaymentOrder.amount.value, event.amountValue)
        assertEquals(testPaymentOrder.amount.currency.currencyCode, event.currency)
        assertNotNull(event.createdAt)
    }

    @Test
    fun `toPaymentOrderCreatedEvent should use current time for createdAt`() {
        val event = mapper.toPaymentOrderCreated(testPaymentOrder)
        
        // With fixed clock, the time should be exactly what we set
        assertEquals(LocalDateTime.now(clock), event.createdAt)
    }

    @Test
    fun `fromEvent should reconstruct PaymentOrder correctly`() {
        val testEvent = createTestPaymentOrderEvent()
        val paymentOrder = mapper.fromEvent(testEvent)

        assertEquals(PaymentOrderId(testEvent.paymentOrderId.toLong()), paymentOrder.paymentOrderId)
        assertEquals(testEvent.publicPaymentOrderId, paymentOrder.publicPaymentOrderId)
        assertEquals(PaymentId(testEvent.paymentId.toLong()), paymentOrder.paymentId)
        assertEquals(testEvent.publicPaymentId, paymentOrder.publicPaymentId)
        assertEquals(SellerId(testEvent.sellerId), paymentOrder.sellerId)
        assertEquals(Amount.of(testEvent.amountValue, Currency(testEvent.currency)), paymentOrder.amount)
        assertEquals(PaymentOrderStatus.valueOf(testEvent.status), paymentOrder.status)
        assertEquals(testEvent.createdAt, paymentOrder.createdAt)
        assertEquals(testEvent.updatedAt, paymentOrder.updatedAt)
        assertEquals(testEvent.retryCount, paymentOrder.retryCount)
        assertEquals(testEvent.retryReason, paymentOrder.retryReason)
        assertEquals(testEvent.lastErrorMessage, paymentOrder.lastErrorMessage)
    }

    @Test
    fun `fromEvent should handle different status values`() {
        val statuses = listOf(
            "INITIATED_PENDING",
            "SUCCESSFUL_FINAL",
            "FAILED_FINAL",
            "FAILED_TRANSIENT_ERROR",
            "PSP_UNAVAILABLE_TRANSIENT"
        )

        statuses.forEach { status ->
            val testEvent = createTestPaymentOrderEvent(status = status)
            val paymentOrder = mapper.fromEvent(testEvent)

            assertEquals(PaymentOrderStatus.valueOf(status), paymentOrder.status)
        }
    }

    @Test
    fun `toPaymentOrderSucceeded should map all fields correctly`() {
        val event = mapper.toPaymentOrderSucceeded(testPaymentOrder)

        assertEquals(testPaymentOrder.paymentOrderId.value.toString(), event.paymentOrderId)
        assertEquals(testPaymentOrder.publicPaymentOrderId, event.publicPaymentOrderId)
        assertEquals(testPaymentOrder.paymentId.value.toString(), event.paymentId)
        assertEquals(testPaymentOrder.publicPaymentId, event.publicPaymentId)
        assertEquals(testPaymentOrder.sellerId.value, event.sellerId)
        assertEquals(testPaymentOrder.amount.value, event.amountValue)
        assertEquals(testPaymentOrder.amount.currency.currencyCode, event.currency)
        assertEquals(testPaymentOrder.status.name, event.status)
        // Note: createdAt and updatedAt are set to current time in the event constructor, not from mapper
        assertNotNull(event.createdAt)
        assertNotNull(event.updatedAt)
    }

    @Test
    fun `toPaymentOrderFailed should map all fields correctly`() {
        val event = mapper.toPaymentOrderFailed(testPaymentOrder)

        assertEquals(testPaymentOrder.paymentOrderId.value.toString(), event.paymentOrderId)
        assertEquals(testPaymentOrder.publicPaymentOrderId, event.publicPaymentOrderId)
        assertEquals(testPaymentOrder.paymentId.value.toString(), event.paymentId)
        assertEquals(testPaymentOrder.publicPaymentId, event.publicPaymentId)
        assertEquals(testPaymentOrder.sellerId.value, event.sellerId)
        assertEquals(testPaymentOrder.amount.value, event.amountValue)
        assertEquals(testPaymentOrder.amount.currency.currencyCode, event.currency)
        assertEquals(testPaymentOrder.status.name, event.status)
        // Note: createdAt and updatedAt are set to current time in the event constructor, not from mapper
        assertNotNull(event.createdAt)
        assertNotNull(event.updatedAt)
    }

    @Test
    fun `toPaymentOrderSucceeded should include status from payment order`() {
        val paymentOrderWithSuccessStatus = createTestPaymentOrder(status = PaymentOrderStatus.SUCCESSFUL_FINAL)
        val event = mapper.toPaymentOrderSucceeded(paymentOrderWithSuccessStatus)
        
        assertEquals("SUCCESSFUL_FINAL", event.status)
    }

    @Test
    fun `toPaymentOrderFailed should include status from payment order`() {
        val paymentOrderWithFailedStatus = createTestPaymentOrder(status = PaymentOrderStatus.FAILED_FINAL)
        val event = mapper.toPaymentOrderFailed(paymentOrderWithFailedStatus)
        
        assertEquals("FAILED_FINAL", event.status)
    }

    @Test
    fun `toPaymentOrderStatusCheckRequested should map all fields correctly`() {
        val event = mapper.toPaymentOrderStatusCheckRequested(testPaymentOrder)

        assertEquals(testPaymentOrder.paymentOrderId.value.toString(), event.paymentOrderId)
        assertEquals(testPaymentOrder.publicPaymentOrderId, event.publicPaymentOrderId)
        assertEquals(testPaymentOrder.paymentId.value.toString(), event.paymentId)
        assertEquals(testPaymentOrder.publicPaymentId, event.publicPaymentId)
        assertEquals(testPaymentOrder.sellerId.value, event.sellerId)
        assertEquals(testPaymentOrder.retryCount, event.retryCount)
        assertEquals(testPaymentOrder.retryReason, event.retryReason)
        assertEquals(testPaymentOrder.status.name, event.status)
        assertEquals(testPaymentOrder.amount.value, event.amountValue)
        assertEquals(testPaymentOrder.amount.currency.currencyCode, event.currency)
        assertNotNull(event.createdAt)
        assertNotNull(event.updatedAt)
    }

    @Test
    fun `toPaymentOrderStatusCheckRequested should use current time for timestamps`() {
        val event = mapper.toPaymentOrderStatusCheckRequested(testPaymentOrder)
        
        // With fixed clock, the time should be exactly what we set
        assertEquals(LocalDateTime.now(clock), event.createdAt)
        assertEquals(LocalDateTime.now(clock), event.updatedAt)
    }

    @Test
    fun `copyWithStatus should create new event with updated status`() {
        val originalEvent = PaymentOrderCreated(
            paymentOrderId = "123",
            publicPaymentOrderId = "paymentorder-123",
            paymentId = "456",
            publicPaymentId = "payment-456",
            sellerId = "seller-789",
            amountValue = 10000L,
            currency = "USD",
            status = "INITIATED_PENDING",
            createdAt = now,
            updatedAt = now,
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )

        val newStatus = "SUCCESSFUL_FINAL"
        val updatedEvent = mapper.copyWithStatus(originalEvent, newStatus)

        assertEquals(newStatus, updatedEvent.status)
        assertEquals(originalEvent.paymentOrderId, updatedEvent.paymentOrderId)
        assertEquals(originalEvent.publicPaymentOrderId, updatedEvent.publicPaymentOrderId)
        assertEquals(originalEvent.paymentId, updatedEvent.paymentId)
        assertEquals(originalEvent.publicPaymentId, updatedEvent.publicPaymentId)
        assertEquals(originalEvent.sellerId, updatedEvent.sellerId)
        assertEquals(originalEvent.amountValue, updatedEvent.amountValue)
        assertEquals(originalEvent.currency, updatedEvent.currency)
        assertEquals(originalEvent.createdAt, updatedEvent.createdAt)
        assertEquals(originalEvent.updatedAt, updatedEvent.updatedAt)
        assertEquals(originalEvent.retryCount, updatedEvent.retryCount)
        assertEquals(originalEvent.retryReason, updatedEvent.retryReason)
        assertEquals(originalEvent.lastErrorMessage, updatedEvent.lastErrorMessage)
    }

    @Test
    fun `copyWithStatus should preserve all other fields`() {
        val originalEvent = PaymentOrderCreated(
            paymentOrderId = "999",
            publicPaymentOrderId = "paymentorder-999",
            paymentId = "888",
            publicPaymentId = "payment-888",
            sellerId = "seller-777",
            amountValue = 50000L,
            currency = "EUR",
            status = "FAILED_TRANSIENT_ERROR",
            createdAt = now.minusHours(1),
            updatedAt = now,
            retryCount = 3,
            retryReason = "Timeout",
            lastErrorMessage = "Connection failed"
        )

        val updatedEvent = mapper.copyWithStatus(originalEvent, "SUCCESSFUL_FINAL")

        assertEquals("SUCCESSFUL_FINAL", updatedEvent.status)
        assertEquals(originalEvent.paymentOrderId, updatedEvent.paymentOrderId)
        assertEquals(originalEvent.publicPaymentOrderId, updatedEvent.publicPaymentOrderId)
        assertEquals(originalEvent.paymentId, updatedEvent.paymentId)
        assertEquals(originalEvent.publicPaymentId, updatedEvent.publicPaymentId)
        assertEquals(originalEvent.sellerId, updatedEvent.sellerId)
        assertEquals(originalEvent.amountValue, updatedEvent.amountValue)
        assertEquals(originalEvent.currency, updatedEvent.currency)
        assertEquals(originalEvent.createdAt, updatedEvent.createdAt)
        assertEquals(originalEvent.updatedAt, updatedEvent.updatedAt)
        assertEquals(originalEvent.retryCount, updatedEvent.retryCount)
        assertEquals(originalEvent.retryReason, updatedEvent.retryReason)
        assertEquals(originalEvent.lastErrorMessage, updatedEvent.lastErrorMessage)
    }

    // Edge Cases

    @Test
    fun `should handle different currencies in mapping`() {
        val currencies = listOf("USD", "EUR", "GBP", "JPY", "TRY")

        currencies.forEach { currency ->
            val paymentOrder = createTestPaymentOrder(currency = currency)
            val event = mapper.toPaymentOrderCreated(paymentOrder)

            assertEquals(currency, event.currency)
        }
    }

    @Test
    fun `should handle different amount values in mapping`() {
        val amounts = listOf(0L, 1L, 100L, 10000L, 999999999L)

        amounts.forEach { amount ->
            val paymentOrder = createTestPaymentOrder(amount = amount)
            val event = mapper.toPaymentOrderCreated(paymentOrder)

            assertEquals(amount, event.amountValue)
        }
    }

    @Test
    fun `should handle retry information correctly`() {
        val paymentOrderWithRetry = createTestPaymentOrder(
            retryCount = 5,
            retryReason = "PSP timeout",
            lastErrorMessage = "Connection failed"
        )

        val event = mapper.toPaymentOrderCreated(paymentOrderWithRetry)

        assertEquals(5, event.retryCount)
        // PaymentOrderCreated event doesn't include retryReason and lastErrorMessage
        // as these are not relevant for the creation event
        assertNull(event.retryReason)
        assertNull(event.lastErrorMessage)
    }

    @Test
    fun `should handle null retry information correctly`() {
        val paymentOrderWithoutRetry = createTestPaymentOrder(
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )

        val event = mapper.toPaymentOrderCreated(paymentOrderWithoutRetry)

        assertEquals(0, event.retryCount)
        assertNull(event.retryReason)
        assertNull(event.lastErrorMessage)
    }

    @Test
    fun `should handle large ID values correctly`() {
        val largePaymentOrderId = 999999999L
        val largePaymentId = 888888888L

        val paymentOrder = createTestPaymentOrder(
            paymentOrderId = PaymentOrderId(largePaymentOrderId),
            paymentId = PaymentId(largePaymentId)
        )

        val event = mapper.toPaymentOrderCreated(paymentOrder)

        assertEquals(largePaymentOrderId.toString(), event.paymentOrderId)
        assertEquals(largePaymentId.toString(), event.paymentId)
    }

    // Helper methods

    private fun createTestPaymentOrder(
        paymentOrderId: PaymentOrderId = PaymentOrderId(123L),
        publicPaymentOrderId: String = "paymentorder-123",
        paymentId: PaymentId = PaymentId(456L),
        publicPaymentId: String = "payment-456",
        sellerId: SellerId = SellerId("seller-789"),
        amount: Long = 10000L,
        currency: String = "USD",
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING,
        createdAt: LocalDateTime = now,
        updatedAt: LocalDateTime = now,
        retryCount: Int = 0,
        retryReason: String? = null,
        lastErrorMessage: String? = null
    ): PaymentOrder {
        return PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId(publicPaymentOrderId)
            .paymentId(paymentId)
            .publicPaymentId(publicPaymentId)
            .sellerId(sellerId)
            .amount(Amount.of(amount, Currency(currency)))
            .status(status)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .retryCount(retryCount)
            .retryReason(retryReason)
            .lastErrorMessage(lastErrorMessage)
            .buildFromPersistence()
    }

    private fun createTestPaymentOrderEvent(
        paymentOrderId: String = "123",
        publicPaymentOrderId: String = "paymentorder-123",
        paymentId: String = "456",
        publicPaymentId: String = "payment-456",
        sellerId: String = "seller-789",
        amountValue: Long = 10000L,
        currency: String = "USD",
        status: String = "INITIATED_PENDING",
        createdAt: LocalDateTime = now,
        updatedAt: LocalDateTime = now,
        retryCount: Int = 0,
        retryReason: String? = null,
        lastErrorMessage: String? = null
    ): PaymentOrderEvent {
        return object : PaymentOrderEvent {
            override val paymentOrderId = paymentOrderId
            override val publicPaymentOrderId = publicPaymentOrderId
            override val paymentId = paymentId
            override val publicPaymentId = publicPaymentId
            override val sellerId = sellerId
            override val amountValue = amountValue
            override val currency = currency
            override val status = status
            override val createdAt = createdAt
            override val updatedAt = updatedAt
            override val retryCount = retryCount
            override val retryReason = retryReason
            override val lastErrorMessage = lastErrorMessage
        }
    }
}
