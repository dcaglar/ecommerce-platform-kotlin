package com.dogancaglar.paymentservice.domain.commands

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine

data class CreatePaymentCommand(
    val orderId: OrderId,
    val buyerId: BuyerId,
    val totalAmount: Amount,
    val paymentLines: List<PaymentLine>        // sellerId + amount each
)