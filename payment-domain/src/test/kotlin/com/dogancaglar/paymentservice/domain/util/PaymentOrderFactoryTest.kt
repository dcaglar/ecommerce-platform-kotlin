package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PaymentOrderFactoryTest {

    private val factory = PaymentOrderFactory()

    @Test
    fun `fromEvent should reconstruct PaymentOrder from event with all fields`() {
        val createdAt = LocalDateTime.now()
        val updatedAt = createdAt.plusHours(1)
        val event = TestPaymentOrderEvent(
            paymentOrderId = "1",
            publicPaymentOrderId = "paymentorder-1",
            paymentId = "100",
            publicPaymentId = "payment-100",
            sellerId = "seller-123",
            amountValue = 100000L, // $1000.00 = 100000 cents
            currency = "USD",
            status = "SUCCESSFUL_FINAL",
            createdAt = createdAt,
            updatedAt = updatedAt,
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )

        val paymentOrder = factory.fromEvent(event)

        assertEquals(PaymentOrderId(1L), paymentOrder.paymentOrderId)
        assertEquals("paymentorder-1", paymentOrder.publicPaymentOrderId)
        assertEquals(PaymentId(100L), paymentOrder.paymentId)
        assertEquals("payment-100", paymentOrder.publicPaymentId)
        assertEquals(SellerId("seller-123"), paymentOrder.sellerId)
        assertEquals(100000L, paymentOrder.amount.quantity)
        assertEquals("USD", paymentOrder.amount.currency.currencyCode)
        assertEquals(PaymentOrderStatus.SUCCESSFUL_FINAL, paymentOrder.status)
        assertEquals(createdAt, paymentOrder.createdAt)
        assertEquals(updatedAt, paymentOrder.updatedAt)
        assertEquals(0, paymentOrder.retryCount)
        assertNull(paymentOrder.retryReason)
        assertNull(paymentOrder.lastErrorMessage)
    }

    @Test
    fun `fromEvent should handle retry information`() {
        val event = TestPaymentOrderEvent(
            paymentOrderId = "1",
            publicPaymentOrderId = "paymentorder-1",
            paymentId = "100",
            publicPaymentId = "payment-100",
            sellerId = "seller-123",
            amountValue = 100000L, // $1000.00 = 100000 cents
            currency = "USD",
            status = "FAILED_TRANSIENT_ERROR",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            retryCount = 3,
            retryReason = "PSP timeout",
            lastErrorMessage = "Connection timeout occurred"
        )

        val paymentOrder = factory.fromEvent(event)

        assertEquals(3, paymentOrder.retryCount) // Factory preserves retry count from event
        assertEquals("PSP timeout", paymentOrder.retryReason) // Factory preserves retry reason
        assertEquals("Connection timeout occurred", paymentOrder.lastErrorMessage) // Factory preserves error message
        assertEquals(PaymentOrderStatus.FAILED_TRANSIENT_ERROR, paymentOrder.status) // Factory preserves status from event
    }

    @Test
    fun `fromEvent should handle different payment statuses`() {
        val statuses = listOf(
            "INITIATED_PENDING" to PaymentOrderStatus.INITIATED_PENDING,
            "SUCCESSFUL_FINAL" to PaymentOrderStatus.SUCCESSFUL_FINAL,
            "FAILED_FINAL" to PaymentOrderStatus.FAILED_FINAL,
            "DECLINED_FINAL" to PaymentOrderStatus.DECLINED_FINAL,
            "FAILED_TRANSIENT_ERROR" to PaymentOrderStatus.FAILED_TRANSIENT_ERROR,
            "PSP_UNAVAILABLE_TRANSIENT" to PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT
        )

        statuses.forEach { (eventStatus, expectedStatus) ->
            val event = TestPaymentOrderEvent(
                paymentOrderId = "1",
                publicPaymentOrderId = "paymentorder-1",
                paymentId = "100",
                publicPaymentId = "payment-100",
                sellerId = "seller-123",
                amountValue = 100000L, // $1000.00 = 100000 cents
                currency = "USD",
                status = eventStatus,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                retryCount = 0,
                retryReason = null,
                lastErrorMessage = null
            )

            val paymentOrder = factory.fromEvent(event)

            assertEquals(
                expectedStatus,
                paymentOrder.status,
                "Factory should preserve status from event $eventStatus"
            )
        }
    }

    @Test
    fun `fromEvent should handle different currencies`() {
        val currencies = listOf("USD", "EUR", "GBP", "JPY", "TRY")

        currencies.forEach { currency ->
            val event = TestPaymentOrderEvent(
                paymentOrderId = "1",
                publicPaymentOrderId = "paymentorder-1",
                paymentId = "100",
                publicPaymentId = "payment-100",
                sellerId = "seller-123",
                amountValue = 100000L, // $1000.00 = 100000 cents
                currency = currency,
                status = "SUCCESSFUL_FINAL",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                retryCount = 0,
                retryReason = null,
                lastErrorMessage = null
            )

            val paymentOrder = factory.fromEvent(event)

            assertEquals(currency, paymentOrder.amount.currency.currencyCode)
        }
    }

    @Test
    fun `fromEvent should handle different amount values`() {
        val amounts = listOf(
            1L,           // $0.01
            10050L,       // $100.50
            999999999L    // $9,999,999.99
        )

        amounts.forEach { amount ->
            val event = TestPaymentOrderEvent(
                paymentOrderId = "1",
                publicPaymentOrderId = "paymentorder-1",
                paymentId = "100",
                publicPaymentId = "payment-100",
                sellerId = "seller-123",
                amountValue = amount,
                currency = "USD",
                status = "SUCCESSFUL_FINAL",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                retryCount = 0,
                retryReason = null,
                lastErrorMessage = null
            )

            val paymentOrder = factory.fromEvent(event)

            assertEquals(amount, paymentOrder.amount.quantity)
        }
    }

    @Test
    fun `fromEvent should convert string IDs to Long correctly`() {
        val event = TestPaymentOrderEvent(
            paymentOrderId = "12345",
            publicPaymentOrderId = "paymentorder-12345",
            paymentId = "67890",
            publicPaymentId = "payment-67890",
            sellerId = "seller-123",
            amountValue = 100000L, // $1000.00 = 100000 cents
            currency = "USD",
            status = "SUCCESSFUL_FINAL",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )

        val paymentOrder = factory.fromEvent(event)

        assertEquals(12345L, paymentOrder.paymentOrderId.value)
        assertEquals(67890L, paymentOrder.paymentId.value)
    }

    @Test
    fun `fromEvent should preserve sellerId as string`() {
        val sellerIds = listOf("seller-1", "seller-abc-123", "9876")

        sellerIds.forEach { sellerId ->
            val event = TestPaymentOrderEvent(
                paymentOrderId = "1",
                publicPaymentOrderId = "paymentorder-1",
                paymentId = "100",
                publicPaymentId = "payment-100",
                sellerId = sellerId,
                amountValue = 100000L, // $1000.00 = 100000 cents
                currency = "USD",
                status = "SUCCESSFUL_FINAL",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                retryCount = 0,
                retryReason = null,
                lastErrorMessage = null
            )

            val paymentOrder = factory.fromEvent(event)

            assertEquals(SellerId(sellerId), paymentOrder.sellerId)
        }
    }

    private data class TestPaymentOrderEvent(
        override val paymentOrderId: String,
        override val publicPaymentOrderId: String,
        override val paymentId: String,
        override val publicPaymentId: String,
        override val sellerId: String,
        override val amountValue: Long,
        override val currency: String,
        override val status: String,
        override val createdAt: LocalDateTime,
        override val updatedAt: LocalDateTime,
        override val retryCount: Int,
        override val retryReason: String?,
        override val lastErrorMessage: String?
    ) : PaymentOrderEvent
}
