// payment-application
package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentOrder

interface PaymentOrderModificationPort {
    fun markAsCaptured(order: PaymentOrder): PaymentOrder
    fun markAsCapturePending(order: PaymentOrder): PaymentOrder
    fun markAsCaptureFailed(order: PaymentOrder): PaymentOrder
}