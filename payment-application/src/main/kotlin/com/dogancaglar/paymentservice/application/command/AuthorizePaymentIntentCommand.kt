package com.dogancaglar.paymentservice.application.command

import com.dogancaglar.paymentservice.domain.model.PaymentMethod
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId

data class AuthorizePaymentIntentCommand(
    val paymentIntentId: PaymentIntentId,
    val paymentMethod: PaymentMethod? = null // Optional - for Stripe Payment Element, payment method is already attached
)