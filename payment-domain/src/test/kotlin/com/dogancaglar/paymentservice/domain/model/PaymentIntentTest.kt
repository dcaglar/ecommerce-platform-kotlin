package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.paymentservice.domain.model.vo.*
import kotlin.test.*

class PaymentIntentTest {

    private val buyerId = BuyerId("buyer-1")
    private val orderId = OrderId("order-1")
    private val currency = Currency("EUR")
    private val line1 = PaymentOrderLine(SellerId("s1"), Amount.of(1000, currency))
    private val line2 = PaymentOrderLine(SellerId("s2"), Amount.of(2000, currency))
    private val lines = listOf(line1, line2)
    private val totalAmount = Amount.of(3000, currency)

    @Test
    fun `createNew should enforce amount equals sum of payment lines`() {
        val intent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(1),
            buyerId = buyerId,
            orderId = orderId,
            totalAmount = totalAmount,
            paymentOrderLines = lines
        )

        assertEquals(PaymentIntentStatus.CREATED, intent.status)
        assertEquals(totalAmount.quantity, lines.sumOf { it.amount.quantity })
    }

    @Test
    fun `createNew should fail if total is different from sum of lines`() {
        val wrongTotal = Amount.of(5000, currency)

        assertFailsWith<IllegalArgumentException> {
            PaymentIntent.createNew(
                paymentIntentId = PaymentIntentId(1),
                buyerId = buyerId,
                orderId = orderId,
                totalAmount = wrongTotal,
                paymentOrderLines = lines
            )
        }
    }

    @Test
    fun `startAuthorization only allowed from CREATED`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )

        val pending = intent.markAuthorizedPending()
        assertEquals(PaymentIntentStatus.PENDING_AUTH, pending.status)
    }

    @Test
    fun `startAuthorization should fail when current status is not CREATED`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAuthorizedPending()

        assertFailsWith<IllegalArgumentException> {
            intent.markAuthorizedPending()
        }
    }

    @Test
    fun `markAuthorized allowed only from PENDING_AUTH`() {
        val pending = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAuthorizedPending()

        val authorized = pending.markAuthorized()
        assertEquals(PaymentIntentStatus.AUTHORIZED, authorized.status)
    }

    @Test
    fun `markAuthorized fails from wrong state`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )

        assertFailsWith<IllegalArgumentException> {
            intent.markAuthorized()
        }
    }

    @Test
    fun `markDeclined transitions correctly`() {
        val pending = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAuthorizedPending()

        val declined = pending.markDeclined()
        assertEquals(PaymentIntentStatus.DECLINED, declined.status)
    }

    @Test
    fun `cancel allowed from CREATED or PENDING_AUTH`() {
        val intent = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        )

        assertEquals(PaymentIntentStatus.CANCELLED, intent.markCancelled().status)

        val pending = intent.copyForTest(status = PaymentIntentStatus.PENDING_AUTH)
        assertEquals(PaymentIntentStatus.CANCELLED, pending.markCancelled().status)
    }

    @Test
    fun `cancel should fail after AUTHORIZED`() {
        val authorized = PaymentIntent.createNew(
            PaymentIntentId(1), buyerId, orderId, totalAmount, lines
        ).markAuthorizedPending()
            .markAuthorized()

        assertFailsWith<IllegalArgumentException> {
            authorized.markCancelled()
        }
    }

    // helper
    private fun PaymentIntent.copyForTest(
        status: PaymentIntentStatus
    ) = PaymentIntent.rehydrate(
        paymentIntentId, buyerId, orderId, totalAmount, paymentOrderLines, status, createdAt, updatedAt
    )
}