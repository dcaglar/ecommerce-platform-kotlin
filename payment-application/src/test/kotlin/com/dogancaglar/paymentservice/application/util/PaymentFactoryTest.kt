package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.vo.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Clock

class PaymentFactoryTest {
    private val clock = Clock.systemUTC()
    private val factory = PaymentFactory(clock)

    @Test
    fun `createPayment creates Payment  with correct ids and data`() {
        val paymentId = PaymentId(123L)
        val orderId = OrderId("order-1")
        val buyerId = BuyerId("buyer-1")
        val totalAmount = Amount.of(200000L, Currency("USD")) // $2000.00 = 200000 cents
        val paymentLines = listOf(
            PaymentLine(sellerId = SellerId("seller-1"), amount = Amount.of(100000L, Currency("USD"))), // $1000.00
            PaymentLine(sellerId = SellerId("seller-2"), amount = Amount.of(100000L, Currency("USD")))  // $1000.00
        )

        val cmd = CreatePaymentCommand(
            orderId = orderId,
            buyerId = buyerId,
            totalAmount = totalAmount,
            paymentLines = paymentLines
        )

        val payment = factory.createPayment(cmd, paymentId)

        Assertions.assertEquals(paymentId, payment.paymentId)
        Assertions.assertEquals("payment-123", payment.publicPaymentId)
        Assertions.assertEquals(orderId, payment.orderId)
        Assertions.assertEquals(buyerId, payment.buyerId)
        Assertions.assertEquals(totalAmount, payment.totalAmount)
    }
}