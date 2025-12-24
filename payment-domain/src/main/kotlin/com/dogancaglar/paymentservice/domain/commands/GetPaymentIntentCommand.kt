package com.dogancaglar.paymentservice.domain.commands

import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId

data class GetPaymentIntentCommand(
    val paymentIntentId: PaymentIntentId
)

