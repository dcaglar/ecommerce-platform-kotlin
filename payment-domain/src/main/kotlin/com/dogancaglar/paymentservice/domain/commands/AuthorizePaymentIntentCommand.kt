package com.dogancaglar.paymentservice.domain.commands

import com.dogancaglar.paymentservice.domain.model.PaymentMethod
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId

data class AuthorizePaymentIntentCommand(
    val paymentIntentId: PaymentIntentId,
    val paymentMethod: PaymentMethod
)