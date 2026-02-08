package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.PaymentMethod
import java.util.concurrent.CompletableFuture

//todo remove idempote4ncy keyparam
interface PspAuthorizationGatewayPort {
    fun createPaymentIntent(paymentIntent: PaymentIntent):  CompletableFuture<PaymentIntent>  // "pi_..."
    fun authorizePaymentIntent(paymentIntent: PaymentIntent, token: PaymentMethod?): PaymentIntent
    fun retrieveClientSecret(pspReference: String): String? // Retrieve clientSecret from Stripe by pspReference

}