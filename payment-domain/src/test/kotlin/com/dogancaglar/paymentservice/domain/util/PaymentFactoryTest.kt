package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.vo.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
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
        val totalAmount = Amount(200000L, "USD") // $2000.00 = 200000 cents
        val paymentLines = listOf(
            PaymentLine(sellerId = SellerId("seller-1"), amount = Amount(100000L, "USD")), // $1000.00
            PaymentLine(sellerId = SellerId("seller-2"), amount = Amount(100000L, "USD"))  // $1000.00
        )

        val cmd = CreatePaymentCommand(
            orderId = orderId,
            buyerId = buyerId,
            totalAmount = totalAmount,
            paymentLines = paymentLines
        )

        val payment = factory.createPayment(cmd, paymentId, paymentOrderIds)

        Assertions.assertEquals(paymentId, payment.paymentId)
        Assertions.assertEquals("payment-123", payment.publicPaymentId)
        Assertions.assertEquals(orderId, payment.orderId)
        Assertions.assertEquals(buyerId, payment.buyerId)
        Assertions.assertEquals(totalAmount, payment.totalAmount)
        Assertions.assertEquals(2, payment.paymentOrders.size)

        payment.paymentOrders.forEachIndexed { idx, order ->
            Assertions.assertEquals(paymentOrderIds[idx], order.paymentOrderId)
            Assertions.assertEquals("paymentorder-${paymentOrderIds[idx].value}", order.publicPaymentOrderId)
            Assertions.assertEquals(paymentId, order.paymentId)
            Assertions.assertEquals(payment.publicPaymentId, order.publicPaymentId)
            Assertions.assertEquals(paymentLines[idx].sellerId, order.sellerId)
            Assertions.assertEquals(paymentLines[idx].amount, order.amount)
            Assertions.assertEquals(payment.createdAt, order.createdAt)
        }
    }
}