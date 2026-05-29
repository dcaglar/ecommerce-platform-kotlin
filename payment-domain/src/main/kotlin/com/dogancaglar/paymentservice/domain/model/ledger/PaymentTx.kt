package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.common.Amount
import java.time.Instant

sealed class PaymentTx {
    abstract val txId: Long
    abstract val txType: String
    abstract val paymentId: Long
    abstract val paymentOrderId: Long?
    abstract val parentTxId: Long?
    abstract val acquirerReference: String
    abstract val amount: Amount
    abstract val createdAt: Instant

    data class Authorization(
        override val txId: Long,
        override val paymentId: Long,
        override val acquirerReference: String,
        override val amount: Amount,
        override val createdAt: Instant = Instant.now()
    ) : PaymentTx() {
        override val txType = "AUTHORIZATION"
        override val paymentOrderId: Long? = null
        override val parentTxId: Long? = null
    }

    data class Capture(
        override val txId: Long,
        override val paymentId: Long,
        override val paymentOrderId: Long,
        val authorizationTxId: Long,
        override val acquirerReference: String,
        override val amount: Amount,
        override val createdAt: Instant = Instant.now()
    ) : PaymentTx() {
        override val txType = "CAPTURE"
        override val parentTxId: Long = authorizationTxId
    }

    data class Refund(
        override val txId: Long,
        override val paymentId: Long,
        override val paymentOrderId: Long,
        val captureTxId: Long,
        override val acquirerReference: String,
        override val amount: Amount,
        override val createdAt: Instant = Instant.now()
    ) : PaymentTx() {
        override val txType = "REFUND"
        override val parentTxId: Long = captureTxId
    }
}
