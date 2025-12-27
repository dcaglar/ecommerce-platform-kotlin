package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.PaymentMethod
import java.util.concurrent.CompletableFuture

//todo remove idempote4ncy keyparam
interface PspAuthorizationGatewayPort {
    fun createIntent(idempotencyKey: String, intent: PaymentIntent):  CompletableFuture<PaymentIntent>  // "pi_..."
    fun confirmIntent(idempotencyKey: String, pspReference: String, token: PaymentMethod?): PaymentIntentStatus
    fun authorize(idempotencyKey: String, paymentIntent: PaymentIntent, token: PaymentMethod?): PaymentIntentStatus
    fun retrieveClientSecret(pspReference: String): String? // Retrieve clientSecret from Stripe by pspReference
}