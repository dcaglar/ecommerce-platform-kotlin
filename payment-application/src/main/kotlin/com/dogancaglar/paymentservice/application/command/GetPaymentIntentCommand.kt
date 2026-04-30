package com.dogancaglar.paymentservice.application.command

import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId

data class GetPaymentIntentCommand(
    val paymentIntentId: PaymentIntentId
)

