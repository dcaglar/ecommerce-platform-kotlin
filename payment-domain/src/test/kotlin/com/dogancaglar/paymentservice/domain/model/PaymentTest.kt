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
    private val paymentLines = listOf(
        PaymentLine(SellerId("seller-1"), Amount.of(10000L, currency))
    )

    // --- ✅ Creation ---

    @Test
    fun `should create new payment with initial state`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)

        assertEquals(paymentId, payment.paymentId)
        assertEquals(buyerId, payment.buyerId)
        assertEquals(orderId, payment.orderId)
        assertEquals(totalAmount, payment.totalAmount)
        assertEquals(Amount.zero(currency), payment.capturedAmount)
        assertEquals(PaymentStatus.CREATED, payment.status)
        assertEquals(1, payment.paymentLines.size)
        assertNotNull(payment.createdAt)
        assertNotNull(payment.updatedAt)
    }

    @Test
    fun `should reject creation with non-positive total amount`() {
        val ex = assertThrows<IllegalArgumentException> {
            Payment.createNew(paymentId, buyerId, orderId, Amount.zero(currency), paymentLines)
        }
        assertTrue(ex.message!!.contains("Total amount must be positive"))
    }

    @Test
    fun `should reject creation with empty payment lines`() {
        val ex = assertThrows<IllegalArgumentException> {
            Payment.createNew(paymentId, buyerId, orderId, totalAmount, emptyList())
        }
        assertTrue(ex.message!!.contains("Payment must have at least one payment line"))
    }

    // --- ✅ Authorization Flow ---

    @Test
    fun `should transition from  CREATED to PENDING_AUTH`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)
        val pending = payment.startAuthorization()

        assertEquals(PaymentStatus.PENDING_AUTH, pending.status)
        assertTrue(pending.updatedAt.isAfter(payment.updatedAt))
    }

    @Test
    fun `should transition from PENDING_AUTH to AUTHORIZED`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)
            .startAuthorization()
        val authorized = payment.authorize()

        assertEquals(PaymentStatus.AUTHORIZED, authorized.status)
        assertTrue(authorized.updatedAt.isAfter(payment.updatedAt))
    }

    @Test
    fun `should transition from PENDING_AUTH to DECLINED`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)
            .startAuthorization()
        val declined = payment.decline()

        assertEquals(PaymentStatus.DECLINED, declined.status)
        assertTrue (declined.updatedAt.isAfter(payment.updatedAt))
    }

    @Test
    fun `should fail to start authorization if not CREATED`() {
        val pending = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)
            .startAuthorization()

        val ex = assertThrows<IllegalArgumentException> { pending.startAuthorization() }
        assertTrue(ex.message!!.contains("Can only start authorization from CREATED"))
    }

    @Test
    fun `should fail to authorize if not PENDING_AUTH`() {
        val created = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)

        val ex = assertThrows<IllegalArgumentException> { created.authorize() }
        assertTrue(ex.message!!.contains("Payment can only be authorized from PENDING_AUTH"))
    }

    @Test
    fun `should fail to authorize if already AUTHORIZED`() {
        val authorized = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)
            .startAuthorization()
            .authorize()

        val ex = assertThrows<IllegalArgumentException> { authorized.authorize() }
        assertTrue(ex.message!!.contains("Payment can only be authorized from PENDING_AUTH"))
    }

    @Test
    fun `should fail to decline if not PENDING_AUTH`() {
        val created = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)

        val ex = assertThrows<IllegalArgumentException> { created.decline() }
        assertTrue(ex.message!!.contains("can only be declined from PENDING_AUTH"))
    }

    // --- ✅ Captured Amount ---

    @Test
    fun `should add captured amount and transition to PARTIALLY_CAPTURED`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)
            .startAuthorization()
            .authorize()

        val partial = payment.addCapturedAmount(Amount.of(4000L, currency)) // €40.00 captured

        assertEquals(Amount.of(4000L, currency), partial.capturedAmount)
        assertEquals(PaymentStatus.PARTIALLY_CAPTURED, partial.status)
    }

    @Test
    fun `should transition to CAPTURED when total captured equals total amount`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)
            .startAuthorization()
            .authorize()

        val captured = payment.addCapturedAmount(Amount.of(10000L, currency))

        assertEquals(totalAmount, captured.capturedAmount)
        assertEquals(PaymentStatus.CAPTURED, captured.status)
    }

    @Test
    fun `should fail when captured amount exceeds total`() {
        val payment = Payment.createNew(paymentId, buyerId, orderId, totalAmount, paymentLines)
            .startAuthorization()
            .authorize()

        val ex = assertThrows<IllegalArgumentException> {
            payment.addCapturedAmount(Amount.of(20000L, currency))
        }
        assertTrue(ex.message!!.contains("Captured amount cannot exceed total"))
    }

    // --- ✅ Payment Lines ---

    @Test
    fun `should create payment with multiple payment lines`() {
        val multipleLines = listOf(
            PaymentLine(SellerId("seller-1"), Amount.of(6000L, currency)),
            PaymentLine(SellerId("seller-2"), Amount.of(4000L, currency))
        )
        val total = Amount.of(10000L, currency)
        
        val payment = Payment.createNew(paymentId, buyerId, orderId, total, multipleLines)

        assertEquals(2, payment.paymentLines.size)
        assertEquals(multipleLines[0].sellerId, payment.paymentLines[0].sellerId)
        assertEquals(multipleLines[1].sellerId, payment.paymentLines[1].sellerId)
    }
}