package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus

/**
 * Port for Payment Service Provider (PSP) operations.
 * Implementations can be real or simulated.
 */
interface PSPClientPort {
    fun charge(order: PaymentOrder): PaymentOrderStatus
    fun chargeRetry(order: PaymentOrder): PaymentOrderStatus
    fun checkPaymentStatus(paymentOrderId: String): PaymentOrderStatus
}