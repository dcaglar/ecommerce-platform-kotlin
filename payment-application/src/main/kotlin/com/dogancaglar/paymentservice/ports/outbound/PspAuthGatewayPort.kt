package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.PaymentMethod

/**
 * Port for Payment Service Provider (PSP) operations.
 * Implementations can be real or simulated.
 */
interface PspAuthGatewayPort {
    fun authorize(idempotencyKey:String,paymentIntent: PaymentIntent,token: PaymentMethod): PaymentIntentStatus
}