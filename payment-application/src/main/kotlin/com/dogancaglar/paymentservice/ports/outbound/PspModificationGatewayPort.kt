package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus

/**
 * Port for Payment Service Provider (PSP) operations.
 * Implementations can be real or simulated.
 */
interface PspModificationGatewayPort {

    fun capture(order: PaymentOrder): PaymentOrderStatus

    fun refund(order: PaymentOrder): PaymentOrderStatus
}