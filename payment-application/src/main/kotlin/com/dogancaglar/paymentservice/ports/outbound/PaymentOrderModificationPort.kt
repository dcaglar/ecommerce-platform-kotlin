// payment-application
package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentOrder

interface PaymentOrderModificationPort {
    fun markPaid(order: PaymentOrder): PaymentOrder
    fun markFailedForRetry(order: PaymentOrder, reason: String?, lastError: String?): PaymentOrder
    fun markPendingAndScheduleStatusCheck(order: PaymentOrder, reason: String?, lastError: String?)
    fun markFinalFailed(order: PaymentOrder, reason: String?): PaymentOrder
}