package com.dogancaglar.paymentservice.domain.model.vo

import com.dogancaglar.paymentservice.domain.model.Amount


data class PaymentOrderLine(
    val sellerId: SellerId,
    val amount: Amount
)