package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.paymentservice.domain.model.vo.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class PaymentOrderTest {

    private val paymentOrderId = PaymentOrderId(1L)
    private val publicPaymentOrderId ="paymentorder-1"
    private val paymentId = PaymentId(10L)
    private val publicPaymentId = "payment-10"
    private val sellerId = SellerId("seller-abc")
    private val amount = Amount.of(1000L, Currency("EUR")) // €10.00

    // --- ✅ Creation tests ---

    @Test
    fun `should create new PaymentOrder successfully`() {
        val order = PaymentOrder.createNew(
            paymentOrderId,
            paymentId,
            sellerId,
            amount
        )

        assertEquals(paymentOrderId, order.paymentOrderId)
        assertEquals(publicPaymentOrderId, order.publicPaymentOrderId)
        assertEquals(paymentId, order.paymentId)
        assertEquals(publicPaymentId, order.publicPaymentId)
        assertEquals(sellerId, order.sellerId)
        assertEquals(amount, order.amount)
        assertEquals(PaymentOrderStatus.INITIATED_PENDING, order.status)
        assertEquals(0, order.retryCount)
        assertNotNull(order.createdAt)
        assertNotNull(order.updatedAt)
    }

    @Test
    fun `should reject PaymentOrder with zero or negative amount`() {
        val ex = assertThrows<IllegalArgumentException> {
            PaymentOrder.createNew(
                paymentOrderId,
                paymentId,
                sellerId,
                Amount.of(-1000, Currency("EUR"))
            )
        }
        assertTrue(ex.message!!.contains("greater than zero"))
    }

    @Test
    fun `should reject PaymentOrder with blank seller id`() {
        val ex = assertThrows<IllegalArgumentException> {
            PaymentOrder.createNew(
                paymentOrderId,
                paymentId,
                SellerId(""),
                amount
            )
        }
        assertTrue(ex.message!!.contains("Seller id cant be blank"))
    }

    // --- ✅ State transition tests ---

    @Test
    fun `should transition from INITIATED_PENDING to CAPTURE_REQUESTED`() {
        val order = PaymentOrder.createNew(
            paymentOrderId,
            paymentId,
            sellerId,
            amount
        )

        val updated = order.markCaptureRequested()

        assertEquals(PaymentOrderStatus.CAPTURE_REQUESTED, updated.status)
        assertTrue(updated.updatedAt.isAfter(order.updatedAt))
    }

    @Test
    fun `should fail to request capture if not INITIATED_PENDING`() {
        val order = PaymentOrder.createNew(
            paymentOrderId,
            paymentId,
            sellerId,
            amount
        ).markCaptureRequested()

        val ex = assertThrows<IllegalArgumentException> { order.markCaptureRequested() }
        assertTrue(ex.message!!.contains("Invalid transtion"))
    }

    @Test
    fun `should transition from CAPTURE_REQUESTED to CAPTURED`() {
        val order = PaymentOrder.createNew(
            paymentOrderId,
            paymentId,
            sellerId,
            amount
        ).markCaptureRequested()

        val captured = order.markAsCaptured()

        assertEquals(PaymentOrderStatus.CAPTURED, captured.status)
        assertTrue(captured.updatedAt.isAfter(order.updatedAt))
    }

    @Test
    fun `should fail to mark captured if not CAPTURE_REQUESTED`() {
        val order = PaymentOrder.createNew(
            paymentOrderId,
            paymentId,
            sellerId,
            amount
        )

        val ex = assertThrows<IllegalArgumentException> { order.markAsCaptured() }
        assertTrue(ex.message!!.contains("Invalid transtion"))
    }

    @Test
    fun `should transition from CAPTURE_REQUESTED to CAPTURE_DECLINED`() {
        val order = PaymentOrder.createNew(
            paymentOrderId,
            paymentId,
            sellerId,
            amount
        ).markCaptureRequested()

        val declined = order.markCaptureDeclined()

        assertEquals(PaymentOrderStatus.CAPTURE_FAILED, declined.status)
        assertTrue(declined.updatedAt.isAfter(order.updatedAt))
    }

    @Test
    fun `should fail to mark capture declined if not CAPTURE_REQUESTED`() {
        val order = PaymentOrder.createNew(
            paymentOrderId,
            paymentId,
            sellerId,
            amount
        )

        val ex = assertThrows<IllegalArgumentException> { order.markCaptureDeclined() }
        assertTrue(ex.message!!.contains("Invalid transtion"))
    }

    // --- ✅ buildFromPersistence test ---

    @Test
    fun `should build from persistence without validation`() {
        val persisted = PaymentOrder.rehydrate(
            paymentOrderId,
            paymentId,
            SellerId("seller-x"),
            Amount.of(2000L, Currency("USD")),
            PaymentOrderStatus.CAPTURED,
            retryCount = 2,
            createdAt = LocalDateTime.now().minusDays(1),
            updatedAt = LocalDateTime.now()
        )

        assertEquals(PaymentOrderStatus.CAPTURED, persisted.status)
        assertEquals(2, persisted.retryCount)
        assertEquals("USD", persisted.amount.currency.currencyCode)
    }
}