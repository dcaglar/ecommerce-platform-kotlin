package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.PaymentMethod

interface PspAuthGatewayPort {
    fun createIntent(idempotencyKey: String, intent: PaymentIntent):  PaymentIntent  // "pi_..."
    fun confirmIntent(idempotencyKey: String, pspReference: String, token: PaymentMethod?): PaymentIntentStatus
    fun authorize(idempotencyKey: String, paymentIntent: PaymentIntent, token: PaymentMethod?): PaymentIntentStatus
}