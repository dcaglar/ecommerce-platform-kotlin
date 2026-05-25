package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus

/**
 * Port for Payment Service Provider (PSP) operations.
 * Implementations can be real or simulated.
 */
interface PspCaptureGatewayPort {
    fun capture(order: PaymentOrder): PaymentOrderStatus
}