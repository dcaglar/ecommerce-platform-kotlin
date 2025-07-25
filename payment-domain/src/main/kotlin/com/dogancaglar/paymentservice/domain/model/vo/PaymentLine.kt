package com.dogancaglar.paymentservice.domain.model.vo

import com.dogancaglar.paymentservice.domain.model.Amount


data class PaymentLine(
    val sellerId: SellerId,
    val amount: Amount
)