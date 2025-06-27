package com.dogancaglar.payment.domain.model.vo

import com.dogancaglar.payment.domain.model.Amount

data class PaymentLine(
    val sellerId: SellerId,
    val amount: Amount
)