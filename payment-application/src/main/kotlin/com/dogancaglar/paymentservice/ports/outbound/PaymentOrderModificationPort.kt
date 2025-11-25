// payment-application
package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId

interface PaymentOrderModificationPort {
    fun updateReturningIdempotent(order: PaymentOrder): PaymentOrder
    fun updateReturningIdempotentInitialCaptureRequest(paymentOrderId: Long): PaymentOrder
    fun findByPaymentOrderId(paymentOrderId: PaymentOrderId): PaymentOrder?
    }