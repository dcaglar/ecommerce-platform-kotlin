package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.*
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.vo.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PaymentOrderDomainEventMapperTest {

    private val fixedClock =
        Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC)

    private val mapper = PaymentOrderDomainEventMapper()

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
        val lines = listOf(
            PaymentOrderLine(SellerId("seller-1"), amount)
        )
        val payment = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(1L),
            buyerId = BuyerId("buyer-1"),
            orderId = OrderId("order-1"),
            totalAmount = Amount.of(5000L, currency),
            paymentOrderLines = lines
        ).markAuthorizedPending().markAuthorized()



        val event = mapper.toPaymentIntentAuthorizedIntentEvent(payment)

        assertEquals("1", event.paymentIntentId)
        assertEquals(payment.paymentIntentId.toPublicPaymentIntentId(), event.publicPaymentIntentId)
        assertEquals("buyer-1", event.buyerId)
        assertEquals("order-1", event.orderId)
        assertEquals(5000L, event.totalAmountValue)
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
        // Timestamp should be close to now (within a few seconds) since Utc.nowInstant() is used
        val now = Utc.nowInstant()
        assertTrue(event.timestamp.isAfter(now.minusSeconds(5)))
        assertTrue(event.timestamp.isBefore(now.plusSeconds(5)))
    }

    // ---------------------------------------------------------
    // 3. CAPTURE COMMAND
    // ---------------------------------------------------------
    @Test
    fun `toPaymentOrderCaptureCommand maps retry count`() {
        // PaymentOrderCaptureCommand requires order to be in CAPTURE_REQUESTED or PENDING_CAPTURE status
        val order = sampleOrder().markCaptureRequested()

        val event = mapper.toPaymentOrderCaptureCommand(order, attempt = 3)

        assertEquals(3, event.attempt)
        assertEquals(order.paymentOrderId.value.toString(), event.paymentOrderId)
        assertEquals(order.paymentOrderId.toPublicPaymentOrderId(), event.publicPaymentOrderId)
    }

    // ---------------------------------------------------------
    // 4. SUCCEEDED + FAILED
    // ---------------------------------------------------------
    @Test
    fun `toPaymentOrderSucceeded maps correctly`() {
        val order = sampleOrder()
        val now = Utc.nowLocalDateTime()
        val event = mapper.toPaymentOrderFinalized(order, now, PaymentOrderStatus.CAPTURED)

        assertEquals("${order.paymentOrderId.value}", event.paymentOrderId)
        assertEquals(order.paymentOrderId.toPublicPaymentOrderId(), event.publicPaymentOrderId)
        assertEquals("CAPTURED", event.status)
    }

    @Test
    fun `toPaymentOrderFinalized maps correctly for failed status`() {
        val order = sampleOrder()
        val now = Utc.nowLocalDateTime()
        val event = mapper.toPaymentOrderFinalized(order, now, PaymentOrderStatus.CAPTURE_FAILED)

        assertEquals("10", event.paymentOrderId)
        assertEquals(order.paymentId.toPublicPaymentId(), event.publicPaymentId)
        assertEquals(order.paymentOrderId.toPublicPaymentOrderId(), event.publicPaymentOrderId)
        assertEquals("CAPTURE_FAILED", event.status)
    }

    // ---------------------------------------------------------
    // 5. FROM EVENT (rehydration)
    // ---------------------------------------------------------

    // ---------------------------------------------------------
    // 6. COPY WITH STATUS
    // ---------------------------------------------------------

}