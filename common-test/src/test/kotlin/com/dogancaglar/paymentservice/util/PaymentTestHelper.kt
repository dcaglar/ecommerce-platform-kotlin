package com.dogancaglar.paymentservice.util

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId

object PaymentTestHelper {

    fun createPaymentIntentCommand(
        buyerId: String = "buyer-123",
        orderId: String = "order-456",
        totalAmount: Long = 10_000,
        currency: String = "EUR",
        sellerId: String = "seller-789"
    ): CreatePaymentIntentCommand {
        val amount = Amount.of(totalAmount, Currency(currency))
        return CreatePaymentIntentCommand(
            buyerId = BuyerId(buyerId),
            orderId = OrderId(orderId),
            totalAmount = amount,
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId(sellerId),
                    amount = amount
                )
            )
        )
    }

    fun createPaymentIntent(
        paymentIntentId: Long = 100L,
        buyerId: String = "buyer-123",
        orderId: String = "order-456",
        totalAmount: Long = 10_000,
        currency: String = "EUR",
        sellerId: String = "seller-789"
    ): PaymentIntent {
        val amount = Amount.of(totalAmount, Currency(currency))
        return PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(paymentIntentId),
            buyerId = BuyerId(buyerId),
            orderId = OrderId(orderId),
            totalAmount = amount,
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId(sellerId),
                    amount = amount
                )
            )
        )
    }
}
