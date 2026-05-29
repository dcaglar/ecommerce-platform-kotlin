package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.ledger.PaymentTx

interface PaymentTxPort {
    fun save(paymentTx: PaymentTx)
    fun findByPaymentId(paymentId: Long): List<PaymentTx>
}
