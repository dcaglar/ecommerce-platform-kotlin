package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.paymentservice.domain.model.vo.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentTest {

    private val paymentId = PaymentId(1L)
    private val buyerId = BuyerId("buyer-xyz")
    private val orderId = OrderId("order-xyz")
    private val currency = Currency("EUR")
    private val totalAmount = Amount.of(10000L, currency) // €100.00

    // --- ✅ Creation ---

    @Test
    fun `should create new payment with initial state`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount)

        assertEquals(paymentId, payment.paymentId)
        assertEquals(buyerId, payment.buyerId)
        assertEquals(orderId, payment.orderId)
        assertEquals(totalAmount, payment.totalAmount)
        assertEquals(Amount.zero(currency), payment.capturedAmount)
        assertEquals(PaymentStatus.PENDING_AUTH, payment.status)
        assertEquals(0, payment.paymentOrders.size)
        assertNotNull(payment.createdAt)
        assertNotNull(payment.updatedAt)
    }

    @Test
    fun `should reject creation with non-positive total amount`() {
        val ex = assertThrows<IllegalArgumentException> {
            Payment.createNew(paymentId, buyerId, orderId, Amount.zero( currency))
        }
        assertTrue(ex.message!!.contains("Total amount must be positive"))
    }

    // --- ✅ Authorize & Decline ---

    @Test
    fun `should transition from PENDING_AUTH to AUTHORIZED`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount)
        val authorized = payment.authorize()

        assertEquals(PaymentStatus.AUTHORIZED, authorized.status)
        assertTrue(authorized.updatedAt.isAfter(payment.updatedAt))
    }

    @Test
    fun `should transition from PENDING_AUTH to DECLINED`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount)
        val declined = payment.decline()

        assertEquals(PaymentStatus.DECLINED, declined.status)
        assertTrue(declined.updatedAt.isAfter(payment.updatedAt))
    }

    @Test
    fun `should fail to authorize if already AUTHORIZED`() {
        val authorized = Payment.createNew(paymentId, buyerId, orderId, totalAmount).authorize()

        val ex = assertThrows<IllegalArgumentException> { authorized.authorize() }
        assertTrue(ex.message!!.contains("Payment can only be authorized"))
    }

    @Test
    fun `should fail to decline if not PENDING_AUTH`() {
        val authorized = Payment.createNew(paymentId,  buyerId, orderId, totalAmount).authorize()

        val ex = assertThrows<IllegalArgumentException> { authorized.decline() }
        assertTrue(ex.message!!.contains("can only be declined"))
    }

    // --- ✅ Captured Amount ---

    @Test
    fun `should add captured amount and transition to CAPTURED_PARTIALLY`() {

        val payment = Payment.createNew(paymentId,  buyerId, orderId, totalAmount)
            .authorize()

        val partial = payment.addCapturedAmount(Amount.of(4000L, currency)) // €40.00 captured

        assertEquals(Amount.of(4000L, currency), partial.capturedAmount)
        assertEquals(PaymentStatus.PARTIALLY_CAPTURED, partial.status)
    }

    @Test
    fun `should transition to CAPTURED when total captured equals total amount`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount)
            .authorize()

        val captured = payment.addCapturedAmount(Amount.of(10000L, currency))

        assertEquals(totalAmount, captured.capturedAmount)
        assertEquals(PaymentStatus.CAPTURED, captured.status)
    }

    @Test
    fun `should fail when captured amount exceeds total`() {
        val payment = Payment.createNew(paymentId,buyerId, orderId, totalAmount)
            .authorize()

        val ex = assertThrows<IllegalArgumentException> {
            payment.addCapturedAmount(Amount.of(20000L, currency))
        }
        assertTrue(ex.message!!.contains("Captured amount cannot exceed total"))
    }

    // --- ✅ addPaymentOrder() ---

    @Test
    fun `should add valid payment order to payment`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount)
        val order = PaymentOrder.createNew(
            paymentOrderId = PaymentOrderId(10L),
            paymentId = paymentId,
            sellerId = SellerId("seller-1"),
            amount = Amount.of(5000L, currency)
        )

        val updated = payment.addPaymentOrder(order)

        assertEquals(1, updated.paymentOrders.size)
        assertEquals(order.paymentOrderId, updated.paymentOrders.first().paymentOrderId)
    }

    @Test
    fun `should reject adding PaymentOrder with mismatched payment id`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount)
        val invalidOrder = PaymentOrder.createNew(
            paymentOrderId = PaymentOrderId(10L),
            paymentId = PaymentId(999L), // ❌ different
            sellerId = SellerId("seller-1"),
            amount = Amount.of(5000L, currency)
        )

        val ex = assertThrows<IllegalArgumentException> { payment.addPaymentOrder(invalidOrder) }
        assertTrue(ex.message!!.contains("must reference the same Payment"))
    }

    @Test
    fun `should reject adding PaymentOrder with currency mismatch`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount)
        val invalidOrder = PaymentOrder.createNew(
            paymentOrderId = PaymentOrderId(10L),
            paymentId = paymentId,
            sellerId = SellerId("seller-1"),
            amount = Amount.of(5000L, Currency("USD")) // ❌ different currency
        )

        val ex = assertThrows<IllegalArgumentException> { payment.addPaymentOrder(invalidOrder) }
        assertTrue(ex.message!!.contains("Currency mismatch"))
    }
}