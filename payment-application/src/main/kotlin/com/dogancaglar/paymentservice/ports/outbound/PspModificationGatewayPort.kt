package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.payment.PspModificationStatus

/**
 * Port for Payment Service Provider (PSP) operations.
 * Implementations can be real or simulated.
 */
interface PspModificationGatewayPort {

    fun capture(paymentIntent: PaymentIntent): PspModificationStatus

    fun refund(paymentIntent: PaymentIntent): PspModificationStatus
}