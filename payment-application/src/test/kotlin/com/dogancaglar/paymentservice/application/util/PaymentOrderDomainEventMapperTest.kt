package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.common.id.PublicIdCodec
import com.dogancaglar.paymentservice.domain.model.*
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.vo.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.*

class PaymentOrderDomainEventMapperTest {

    private val fixedClock =
        Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC)

    private val mapper = PaymentOrderDomainEventMapper(fixedClock)

    private val currency = Currency("EUR")
    private val amount = Amount.of(5000L, currency)

    private fun sampleOrder(): PaymentOrder =
        PaymentOrder.createNew(
            paymentOrderId = PaymentOrderId(10L),
            paymentId = PaymentId(1L),
            sellerId = SellerId("seller-1"),
            amount = amount
        )

    // ---------------------------------------------------------
    // 1. PAYMENT AUTHORIZED
    // ---------------------------------------------------------
    @Test
    fun `toPaymentAuthorized maps payment and lines correctly`() {
        val payment = Payment.createNew(
            paymentId = PaymentId(1L),
            buyerId = BuyerId("buyer-1"),
            orderId = OrderId("order-1"),
            totalAmount = Amount.of(10000L, currency),
            clock = fixedClock
        )

        val lines = listOf(
            PaymentLine(SellerId("seller-1"), amount)
        )

        val event = mapper.toPaymentAuthorized(payment, lines)

        assertEquals("1", event.paymentId)
        assertEquals(payment.paymentId.toPublicPaymentId(), event.publicPaymentId)
        assertEquals("buyer-1", event.buyerId)
        assertEquals("order-1", event.orderId)
        assertEquals(10000L, event.totalAmountValue)
        assertEquals("EUR", event.currency)
        assertEquals(1, event.paymentLines.size)
    }

    // ---------------------------------------------------------
    // 2. CREATED
    // ---------------------------------------------------------
    @Test
    fun `toPaymentOrderCreated maps all fields correctly`() {
        val order = sampleOrder()

        val event = mapper.toPaymentOrderCreated(order)

        assertEquals("10", event.paymentOrderId)
        assertEquals(order.paymentOrderId.toPublicPaymentOrderId(), event.publicPaymentOrderId)
        assertEquals("1", event.paymentId)
        assertEquals(order.paymentId.toPublicPaymentId(), event.publicPaymentId)
        assertEquals("seller-1", event.sellerId)
        assertEquals(5000L, event.amountValue)
        assertEquals("EUR", event.currency)
        assertEquals(order.status.name, event.status)
        assertEquals(order.retryCount, event.retryCount)
        assertEquals(LocalDateTime.ofInstant(fixedClock.instant(), fixedClock.zone), event.createdAt)
    }

    // ---------------------------------------------------------
    // 3. CAPTURE COMMAND
    // ---------------------------------------------------------
    @Test
    fun `toPaymentOrderCaptureCommand maps retry count`() {
        val order = sampleOrder()

        val event = mapper.toPaymentOrderCaptureCommand(order, attempt = 3)

        assertEquals(3, event.retryCount)
        assertEquals(order.paymentOrderId.value.toString(), event.paymentOrderId)
        assertEquals(order.paymentOrderId.toPublicPaymentOrderId(), event.publicPaymentOrderId)
    }

    // ---------------------------------------------------------
    // 4. SUCCEEDED + FAILED
    // ---------------------------------------------------------
    @Test
    fun `toPaymentOrderSucceeded maps correctly`() {
        val order = sampleOrder()
        val event = mapper.toPaymentOrderSucceeded(order)

        assertEquals("${order.paymentOrderId.value}", event.paymentOrderId)
        assertEquals(order.paymentOrderId.toPublicPaymentOrderId(), event.publicPaymentOrderId)
        assertEquals(order.status.name, event.status)
    }

    @Test
    fun `toPaymentOrderFailed maps correctly`() {
        val order = sampleOrder()
        val event = mapper.toPaymentOrderFailed(order)

        assertEquals("10", event.paymentOrderId)
        assertEquals(order.paymentId.toPublicPaymentId(), event.publicPaymentId)
        assertEquals(order.paymentOrderId.toPublicPaymentOrderId(), event.publicPaymentOrderId)
        assertEquals(order.status.name, event.status)
    }

    // ---------------------------------------------------------
    // 5. FROM EVENT (rehydration)
    // ---------------------------------------------------------
    @Test
    fun `fromEvent reconstructs domain PaymentOrder`() {
        val order = sampleOrder()
        val event = mapper.toPaymentOrderCreated(order)

        val rebuilt = mapper.fromEvent(event)

        assertEquals(order.paymentOrderId, rebuilt.paymentOrderId)
        assertEquals(order.paymentId, rebuilt.paymentId)
        assertEquals(order.sellerId, rebuilt.sellerId)
        assertEquals(order.amount, rebuilt.amount)
        assertEquals(order.status, rebuilt.status)
        assertEquals(order.retryCount, rebuilt.retryCount)
    }

    // ---------------------------------------------------------
    // 6. COPY WITH STATUS
    // ---------------------------------------------------------
    @Test
    fun `copyWithStatus changes only status`() {
        val order = sampleOrder()
        val event = mapper.toPaymentOrderCreated(order)

        val updated = mapper.copyWithStatus(event, "CAPTURED")

        assertEquals("CAPTURED", updated.status)
        assertEquals(event.paymentOrderId, updated.paymentOrderId)
        assertEquals(event.amountValue, updated.amountValue)
    }
}