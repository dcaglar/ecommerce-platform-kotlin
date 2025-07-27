package com.dogancaglar.payment.domain.model.command

import com.dogancaglar.payment.domain.model.Amount
import com.dogancaglar.payment.domain.model.vo.BuyerId
import com.dogancaglar.payment.domain.model.vo.OrderId
import com.dogancaglar.payment.domain.model.vo.PaymentLine

data class CreatePaymentCommand(
    val orderId: OrderId,
    val buyerId: BuyerId,
    val totalAmount: Amount,
    val paymentLines: List<PaymentLine>        // sellerId + amount each
)