package com.dogancaglar.payment.domain.factory

import com.dogancaglar.payment.domain.model.Amount
import com.dogancaglar.payment.domain.model.CreatePaymentCommand
import com.dogancaglar.payment.domain.model.vo.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock

class PaymentFactoryTest {
    private val clock = Clock.systemUTC()
    private val factory = PaymentFactory(clock)

    @Test
    fun `createPayment creates Payment and PaymentOrders with correct ids and data`() {
        val paymentId = PaymentId(123L)
        val paymentOrderIds = listOf(PaymentOrderId(1L), PaymentOrderId(2L))
        val orderId = OrderId("order-1")
        val buyerId = BuyerId("buyer-1")
        val totalAmount = Amount(BigDecimal.valueOf(2000), "USD")
        val paymentLines = listOf(
            PaymentLine(sellerId = SellerId("seller-1"), amount = Amount(BigDecimal.valueOf(1000), "USD")),
            PaymentLine(sellerId = SellerId("seller-2"), amount = Amount(BigDecimal.valueOf(1000), "USD"))
        )

        val cmd = CreatePaymentCommand(
            orderId = orderId,
            buyerId = buyerId,
            totalAmount = totalAmount,
            paymentLines = paymentLines
        )

        val payment = factory.createPayment(cmd, paymentId, paymentOrderIds)

        assertEquals(paymentId, payment.paymentId)
        assertEquals("payment-123", payment.publicPaymentId)
        assertEquals(orderId, payment.orderId)
        assertEquals(buyerId, payment.buyerId)
        assertEquals(totalAmount, payment.totalAmount)
        assertEquals(2, payment.paymentOrders.size)

        payment.paymentOrders.forEachIndexed { idx, order ->
            assertEquals(paymentOrderIds[idx], order.paymentOrderId)
            assertEquals("paymentorder-${paymentOrderIds[idx].value}", order.publicPaymentOrderId)
            assertEquals(paymentId, order.paymentId)
            assertEquals(payment.publicPaymentId, order.publicPaymentId)
            assertEquals(paymentLines[idx].sellerId, order.sellerId)
            assertEquals(paymentLines[idx].amount, order.amount)
            assertEquals(payment.createdAt, order.createdAt)
        }
    }
}

