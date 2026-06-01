// payment-application
package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.payment.Payment
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder

interface PaymentTransactionalFacadePort {
    fun handleAuthorized(authorizedPaymentIntent: PaymentIntent, paymentAuthorizedOutboxEvent: OutboxEvent)
    }