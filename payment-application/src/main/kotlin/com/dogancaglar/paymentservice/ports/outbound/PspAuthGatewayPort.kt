package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.PaymentStatus

/**
 * Port for Payment Service Provider (PSP) operations.
 * Implementations can be real or simulated.
 */
interface PspAuthGatewayPort {
    fun auth(order: Payment): PaymentStatus
}