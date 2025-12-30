package com.dogancaglar.paymentservice.domain.commands

import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId

data class ProcessPaymentIntentUpdateCommand(
    val paymentIntentId: PaymentIntentId,
    val pspReference: String,
    val clientSecret: String?,
    val status: PaymentIntentStatus
)