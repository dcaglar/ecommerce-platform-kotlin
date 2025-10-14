package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PaymentOrderTest {

    @Test
    fun `createNew should create PaymentOrder with INITIATED_PENDING status`() {
        val now = LocalDateTime.now()
        val paymentOrderId = PaymentOrderId(1L)
        val paymentId = PaymentId(100L)
        val sellerId = SellerId("seller-123")
        val amount = Amount(100000L, "USD") // $1000.00 = 100000 cents

        val paymentOrder = PaymentOrder.createNew(
            paymentOrderId = paymentOrderId,
            publicPaymentOrderId = "paymentorder-1",
            paymentId = paymentId,
            publicPaymentId = "payment-100",
            sellerId = sellerId,
            amount = amount,
            createdAt = now
        )

        assertEquals(paymentOrderId, paymentOrder.paymentOrderId)
        assertEquals("paymentorder-1", paymentOrder.publicPaymentOrderId)
        assertEquals(paymentId, paymentOrder.paymentId)
        assertEquals("payment-100", paymentOrder.publicPaymentId)
        assertEquals(sellerId, paymentOrder.sellerId)
        assertEquals(amount, paymentOrder.amount)
        assertEquals(PaymentOrderStatus.INITIATED_PENDING, paymentOrder.status)
        assertEquals(now, paymentOrder.createdAt)
        assertEquals(now, paymentOrder.updatedAt)
        assertEquals(0, paymentOrder.retryCount)
        assertNull(paymentOrder.retryReason)
        assertNull(paymentOrder.lastErrorMessage)
    }

    @Test
    fun `reconstructFromPersistence should recreate PaymentOrder with all fields`() {
        val now = LocalDateTime.now()
        val updatedAt = now.plusHours(1)
        val paymentOrderId = PaymentOrderId(1L)
        val paymentId = PaymentId(100L)
        val sellerId = SellerId("seller-123")
        val amount = Amount(100000L, "USD") // $1000.00 = 100000 cents

        val paymentOrder = PaymentOrder.reconstructFromPersistence(
            paymentOrderId = paymentOrderId,
            publicPaymentOrderId = "paymentorder-1",
            paymentId = paymentId,
            publicPaymentId = "payment-100",
            sellerId = sellerId,
            amount = amount,
            status = PaymentOrderStatus.FAILED_TRANSIENT_ERROR,
            createdAt = now,
            updatedAt = updatedAt,
            retryCount = 3,
            retryReason = "PSP timeout",
            lastErrorMessage = "Connection timeout"
        )

        assertEquals(paymentOrderId, paymentOrder.paymentOrderId)
        assertEquals(PaymentOrderStatus.FAILED_TRANSIENT_ERROR, paymentOrder.status)
        assertEquals(now, paymentOrder.createdAt)
        assertEquals(updatedAt, paymentOrder.updatedAt)
        assertEquals(3, paymentOrder.retryCount)
        assertEquals("PSP timeout", paymentOrder.retryReason)
        assertEquals("Connection timeout", paymentOrder.lastErrorMessage)
    }

    @Test
    fun `markAsFailed should change status to FAILED_TRANSIENT_ERROR`() {
        val paymentOrder = createTestPaymentOrder(status = PaymentOrderStatus.INITIATED_PENDING)

        val updated = paymentOrder.markAsFailed()

        assertEquals(PaymentOrderStatus.FAILED_TRANSIENT_ERROR, updated.status)
        assertEquals(paymentOrder.paymentOrderId, updated.paymentOrderId)
        assertEquals(paymentOrder.amount, updated.amount)
    }

    @Test
    fun `markAsPaid should change status to SUCCESSFUL_FINAL`() {
        val paymentOrder = createTestPaymentOrder(status = PaymentOrderStatus.INITIATED_PENDING)

        val updated = paymentOrder.markAsPaid()

        assertEquals(PaymentOrderStatus.SUCCESSFUL_FINAL, updated.status)
    }

    @Test
    fun `markAsPending should change status to PENDING_STATUS_CHECK_LATER`() {
        val paymentOrder = createTestPaymentOrder(status = PaymentOrderStatus.INITIATED_PENDING)

        val updated = paymentOrder.markAsPending()

        assertEquals(PaymentOrderStatus.PENDING_STATUS_CHECK_LATER, updated.status)
    }

    @Test
    fun `markAsFinalizedFailed should change status to FAILED_FINAL`() {
        val paymentOrder = createTestPaymentOrder(status = PaymentOrderStatus.INITIATED_PENDING)

        val updated = paymentOrder.markAsFinalizedFailed()

        assertEquals(PaymentOrderStatus.FAILED_FINAL, updated.status)
    }

    @Test
    fun `incrementRetry should increase retry count by 1`() {
        val paymentOrder = createTestPaymentOrder(retryCount = 2)

        val updated = paymentOrder.incrementRetry()

        assertEquals(3, updated.retryCount)
        assertEquals(paymentOrder.status, updated.status)
    }

    @Test
    fun `withRetryReason should set retry reason`() {
        val paymentOrder = createTestPaymentOrder()

        val updated = paymentOrder.withRetryReason("PSP unavailable")

        assertEquals("PSP unavailable", updated.retryReason)
        assertEquals(paymentOrder.retryCount, updated.retryCount)
    }

    @Test
    fun `withLastError should set last error message`() {
        val paymentOrder = createTestPaymentOrder()

        val updated = paymentOrder.withLastError("Network timeout")

        assertEquals("Network timeout", updated.lastErrorMessage)
    }

    @Test
    fun `withUpdatedAt should set updated timestamp`() {
        val paymentOrder = createTestPaymentOrder()
        val newTimestamp = LocalDateTime.now().plusHours(2)

        val updated = paymentOrder.withUpdatedAt(newTimestamp)

        assertEquals(newTimestamp, updated.updatedAt)
        assertNotEquals(paymentOrder.updatedAt, updated.updatedAt)
    }

    @Test
    fun `isTerminal should return true for SUCCESSFUL_FINAL status`() {
        val paymentOrder = createTestPaymentOrder(status = PaymentOrderStatus.SUCCESSFUL_FINAL)

        assertTrue(paymentOrder.isTerminal())
    }

    @Test
    fun `isTerminal should return true for FAILED_FINAL status`() {
        val paymentOrder = createTestPaymentOrder(status = PaymentOrderStatus.FAILED_FINAL)

        assertTrue(paymentOrder.isTerminal())
    }

    @Test
    fun `isTerminal should return false for INITIATED_PENDING status`() {
        val paymentOrder = createTestPaymentOrder(status = PaymentOrderStatus.INITIATED_PENDING)

        assertFalse(paymentOrder.isTerminal())
    }

    @Test
    fun `isTerminal should return false for FAILED_TRANSIENT_ERROR status`() {
        val paymentOrder = createTestPaymentOrder(status = PaymentOrderStatus.FAILED_TRANSIENT_ERROR)

        assertFalse(paymentOrder.isTerminal())
    }

    @Test
    fun `isTerminal should return false for PENDING_STATUS_CHECK_LATER status`() {
        val paymentOrder = createTestPaymentOrder(status = PaymentOrderStatus.PENDING_STATUS_CHECK_LATER)

        assertFalse(paymentOrder.isTerminal())
    }

    @Test
    fun `chaining operations should work correctly`() {
        val paymentOrder = createTestPaymentOrder()
        val newTimestamp = LocalDateTime.now().plusHours(1)

        val updated = paymentOrder
            .incrementRetry()
            .withRetryReason("Timeout")
            .withLastError("Connection error")
            .withUpdatedAt(newTimestamp)
            .markAsFailed()

        assertEquals(1, updated.retryCount)
        assertEquals("Timeout", updated.retryReason)
        assertEquals("Connection error", updated.lastErrorMessage)
        assertEquals(newTimestamp, updated.updatedAt)
        assertEquals(PaymentOrderStatus.FAILED_TRANSIENT_ERROR, updated.status)
    }

    @Test
    fun `copy should preserve immutability`() {
        val original = createTestPaymentOrder()
        val updated = original.markAsFailed()

        assertNotEquals(original.status, updated.status)
        assertEquals(PaymentOrderStatus.INITIATED_PENDING, original.status)
        assertEquals(PaymentOrderStatus.FAILED_TRANSIENT_ERROR, updated.status)
    }

    private fun createTestPaymentOrder(
        paymentOrderId: PaymentOrderId = PaymentOrderId(1L),
        publicPaymentOrderId: String = "paymentorder-1",
        paymentId: PaymentId = PaymentId(100L),
        publicPaymentId: String = "payment-100",
        sellerId: SellerId = SellerId("seller-123"),
        amount: Amount = Amount(100000L, "USD"), // $1000.00 = 100000 cents
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now(),
        retryCount: Int = 0,
        retryReason: String? = null,
        lastErrorMessage: String? = null
    ): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = paymentOrderId,
            publicPaymentOrderId = publicPaymentOrderId,
            paymentId = paymentId,
            publicPaymentId = publicPaymentId,
            sellerId = sellerId,
            amount = amount,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            retryCount = retryCount,
            retryReason = retryReason,
            lastErrorMessage = lastErrorMessage
        )
    }
}
