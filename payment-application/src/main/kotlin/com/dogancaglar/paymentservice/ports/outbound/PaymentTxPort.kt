package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.ledger.Tx

interface PaymentTxPort {
    fun save(tx: Tx)
    fun findByPaymentId(paymentId: Long): List<Tx>
}
