// payment-application
package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId

interface PaymentTransactionalFacadePort {
    fun handleAuthorized(authorizedPaymentIntent: PaymentIntent, payment: Payment, paymentOrderList:List<PaymentOrder>, outboxEventList:List<OutboxEvent>)
    }