package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.paymentservice.domain.model.Currency

import com.dogancaglar.paymentservice.domain.model.vo.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PaymentTest {

    @Test
    fun `Payment builder should create Payment with INITIATED status and payment orders`() {
        val paymentId = PaymentId(1L)
        val buyerId = BuyerId("buyer-123")
        val orderId = OrderId("order-456")
        val totalAmount = Amount.of(200000L, Currency("USD")) // $2000.00 = 200000 cents
        val now = LocalDateTime.now()
        val paymentOrders = listOf(
            createTestPaymentOrder(paymentOrderId = PaymentOrderId(1L)),
            createTestPaymentOrder(paymentOrderId = PaymentOrderId(2L))
        )

        val payment = Payment.Builder()
            .paymentId(paymentId)
            .publicPaymentId("payment-1")
            .buyerId(buyerId)
            .orderId(orderId)
            .totalAmount(totalAmount)
            .createdAt(now)
            .paymentOrders(paymentOrders)
            .build()

        assertEquals(paymentId, payment.paymentId)
        assertEquals("payment-1", payment.publicPaymentId)
        assertEquals(buyerId, payment.buyerId)
        assertEquals(orderId, payment.orderId)
        assertEquals(totalAmount, payment.totalAmount)
        assertEquals(PaymentStatus.INITIATED, payment.status)
        assertEquals(now, payment.createdAt)
        assertEquals(2, payment.paymentOrders.size)
        assertEquals(paymentOrders, payment.paymentOrders)
    }

    @Test
    fun `Payment builder should create Payment with empty paymentOrders list`() {
        val payment = Payment.Builder()
            .paymentId(PaymentId(1L))
            .publicPaymentId("payment-1")
            .buyerId(BuyerId("buyer-123"))
            .orderId(OrderId("order-456"))
            .totalAmount(Amount.of(200000L, Currency("USD"))) // $2000.00 = 200000 cents
            .createdAt(LocalDateTime.now())
            .paymentOrders(emptyList())
            .build()

        assertEquals(PaymentStatus.INITIATED, payment.status)
        assertTrue(payment.paymentOrders.isEmpty())
    }

    @Test
    fun `Payment builder should recreate Payment with all fields from persistence`() {
        val paymentId = PaymentId(1L)
        val buyerId = BuyerId("buyer-123")
        val orderId = OrderId("order-456")
        val totalAmount = Amount.of(200000L, Currency("USD")) // $2000.00 = 200000 cents
        val now = LocalDateTime.now()
        val paymentOrders = listOf(
            createTestPaymentOrder(paymentOrderId = PaymentOrderId(1L))
        )

        val payment = Payment.Builder()
            .paymentId(paymentId)
            .publicPaymentId("payment-1")
            .buyerId(buyerId)
            .orderId(orderId)
            .totalAmount(totalAmount)
            .status(PaymentStatus.SUCCESS)
            .createdAt(now)
            .paymentOrders(paymentOrders)
            .build()

        assertEquals(paymentId, payment.paymentId)
        assertEquals("payment-1", payment.publicPaymentId)
        assertEquals(buyerId, payment.buyerId)
        assertEquals(orderId, payment.orderId)
        assertEquals(totalAmount, payment.totalAmount)
        assertEquals(PaymentStatus.SUCCESS, payment.status)
        assertEquals(now, payment.createdAt)
        assertEquals(1, payment.paymentOrders.size)
    }

    @Test
    fun `markAsPaid should change status to SUCCESS`() {
        val payment = createTestPayment(status = PaymentStatus.INITIATED)

        val updated = payment.markAsPaid()

        assertEquals(PaymentStatus.SUCCESS, updated.status)
        assertEquals(payment.paymentId, updated.paymentId)
        assertEquals(payment.totalAmount, updated.totalAmount)
        assertEquals(payment.paymentOrders, updated.paymentOrders)
    }

    @Test
    fun `markAsFailed should change status to FAILED`() {
        val payment = createTestPayment(status = PaymentStatus.INITIATED)

        val updated = payment.markAsFailed()

        assertEquals(PaymentStatus.FAILED, updated.status)
        assertEquals(payment.paymentId, updated.paymentId)
        assertEquals(payment.totalAmount, updated.totalAmount)
    }

    @Test
    fun `addPaymentOrder should add a new payment order to the list`() {
        val initialOrder = createTestPaymentOrder(paymentOrderId = PaymentOrderId(1L))
        val payment = createTestPayment(paymentOrders = listOf(initialOrder))

        val newOrder = createTestPaymentOrder(paymentOrderId = PaymentOrderId(2L))
        val updated = payment.addPaymentOrder(newOrder)

        assertEquals(2, updated.paymentOrders.size)
        assertTrue(updated.paymentOrders.contains(initialOrder))
        assertTrue(updated.paymentOrders.contains(newOrder))
        assertEquals(payment.status, updated.status)
    }

    @Test
    fun `addPaymentOrder should work on payment with empty orders list`() {
        val payment = createTestPayment(paymentOrders = emptyList())

        val newOrder = createTestPaymentOrder(paymentOrderId = PaymentOrderId(1L))
        val updated = payment.addPaymentOrder(newOrder)

        assertEquals(1, updated.paymentOrders.size)
        assertEquals(newOrder, updated.paymentOrders.first())
    }

    @Test
    fun `addPaymentOrder should preserve immutability`() {
        val initialOrder = createTestPaymentOrder(paymentOrderId = PaymentOrderId(1L))
        val payment = createTestPayment(paymentOrders = listOf(initialOrder))

        val newOrder = createTestPaymentOrder(paymentOrderId = PaymentOrderId(2L))
        val updated = payment.addPaymentOrder(newOrder)

        assertEquals(1, payment.paymentOrders.size)
        assertEquals(2, updated.paymentOrders.size)
    }

    @Test
    fun `Builder should create Payment with all fields`() {
        val paymentId = PaymentId(1L)
        val buyerId = BuyerId("buyer-123")
        val orderId = OrderId("order-456")
        val totalAmount = Amount.of(200000L, Currency("USD")) // $2000.00 = 200000 cents
        val now = LocalDateTime.now()
        val paymentOrders = listOf(createTestPaymentOrder())

        val payment = Payment.Builder()
            .paymentId(paymentId)
            .publicPaymentId("payment-1")
            .buyerId(buyerId)
            .orderId(orderId)
            .totalAmount(totalAmount)
            .status(PaymentStatus.SUCCESS)
            .createdAt(now)
            .paymentOrders(paymentOrders)
            .build()

        assertEquals(paymentId, payment.paymentId)
        assertEquals("payment-1", payment.publicPaymentId)
        assertEquals(buyerId, payment.buyerId)
        assertEquals(orderId, payment.orderId)
        assertEquals(totalAmount, payment.totalAmount)
        assertEquals(PaymentStatus.SUCCESS, payment.status)
        assertEquals(now, payment.createdAt)
        assertEquals(paymentOrders, payment.paymentOrders)
    }



    @Test
    fun `copy should preserve immutability when changing status`() {
        val original = createTestPayment(status = PaymentStatus.INITIATED)
        val updated = original.markAsPaid()

        assertNotEquals(original.status, updated.status)
        assertEquals(PaymentStatus.INITIATED, original.status)
        assertEquals(PaymentStatus.SUCCESS, updated.status)
    }

    @Test
    fun `multiple status changes should be independent`() {
        val payment = createTestPayment(status = PaymentStatus.INITIATED)

        val paid = payment.markAsPaid()
        val failed = payment.markAsFailed()

        assertEquals(PaymentStatus.INITIATED, payment.status)
        assertEquals(PaymentStatus.SUCCESS, paid.status)
        assertEquals(PaymentStatus.FAILED, failed.status)
    }

    private fun createTestPayment(
        paymentId: PaymentId = PaymentId(1L),
        publicPaymentId: String = "payment-1",
        buyerId: BuyerId = BuyerId("buyer-123"),
        orderId: OrderId = OrderId("order-456"),
        totalAmount: Amount = Amount.of(200000L, Currency("USD")), // $2000.00 = 200000 cents
        status: PaymentStatus = PaymentStatus.INITIATED,
        createdAt: LocalDateTime = LocalDateTime.now(),
        paymentOrders: List<PaymentOrder> = emptyList()
    ): Payment {
        return Payment.Builder()
            .paymentId(paymentId)
            .publicPaymentId(publicPaymentId)
            .buyerId(buyerId)
            .orderId(orderId)
            .totalAmount(totalAmount)
            .status(status)
            .createdAt(createdAt)
            .paymentOrders(paymentOrders)
            .build()
    }

    private fun createTestPaymentOrder(
        paymentOrderId: PaymentOrderId = PaymentOrderId(1L),
        paymentId: PaymentId = PaymentId(1L),
        sellerId: SellerId = SellerId("seller-123"),
        amount: Amount = Amount.of(100000L, Currency("USD")) // $1000.00 = 100000 cents
    ): PaymentOrder {
        return PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("paymentorder-${paymentOrderId.value}")
            .paymentId(paymentId)
            .publicPaymentId("payment-${paymentId.value}")
            .sellerId(sellerId)
            .amount(amount)
            .createdAt(LocalDateTime.now())
            .buildNew()
    }
}
