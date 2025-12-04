package com.dogancaglar.paymentservice.domain.commands

import com.dogancaglar.paymentservice.domain.model.vo.PaymentId

data class AuthorizePaymentCommand(
    val paymentId: PaymentId
)