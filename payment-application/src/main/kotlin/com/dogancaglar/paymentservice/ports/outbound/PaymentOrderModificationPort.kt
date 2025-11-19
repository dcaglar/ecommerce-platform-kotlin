// payment-application
package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentOrder

interface PaymentOrderModificationPort {
    fun markAsCaptured(order: PaymentOrder): PaymentOrder
    fun markAsCapturePending(paymentOrderId: Long): PaymentOrder
    fun markAsCaptureRequested(paymentOrderId: Long): PaymentOrder
    fun markAsCaptureFailed(order: PaymentOrder): PaymentOrder
}