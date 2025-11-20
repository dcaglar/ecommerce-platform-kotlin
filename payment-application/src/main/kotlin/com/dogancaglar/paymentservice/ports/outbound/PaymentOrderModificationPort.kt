// payment-application
package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId

interface PaymentOrderModificationPort {
    fun markAsCaptured(order: PaymentOrder): PaymentOrder
    fun markAsCapturePending(order: PaymentOrder): PaymentOrder
    fun markAsCaptureRequested(paymentOrderId: Long): PaymentOrder
    fun markAsCaptureFailed(order: PaymentOrder): PaymentOrder
    fun findByPaymentOrderId(paymentOrderId: PaymentOrderId): PaymentOrder?
    }