package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.paymentservice.domain.model.payment.InternalTransfer

data class InternalTransferCommand(
    val transferId: Long,
    val internalTransferTxId: Long,
    val sourceTransactionId: Long,
    override val amountValue: Long,
    override val currency: String,
    val targetAccountType: String,
    val targetEntityId: String,
    val sourceAccountType: String,
    val sourceEntityId: String,
    val status: String,
    override val paymentIntentId: String,
    override val publicPaymentIntentId: String,
    override val timestamp: java.time.Instant = com.dogancaglar.common.time.Utc.nowInstant()
) : PaymentBaseEvent(paymentIntentId, publicPaymentIntentId, amountValue, currency, timestamp) {
    override val eventType: String = EventType.INTERNAL_TRANSFER_COMMAND

    companion object {
        fun from(transfer: InternalTransfer, txId: Long, paymentIntentId: String, publicPaymentIntentId: String): InternalTransferCommand {
            return InternalTransferCommand(
                transferId = transfer.transferId.value,
                internalTransferTxId = txId,
                sourceTransactionId = transfer.sourceTransactionId.value,
                amountValue = transfer.amount.quantity,
                currency = transfer.amount.currency.currencyCode,
                targetAccountType = transfer.targetAccountType.name,
                targetEntityId = transfer.targetEntityId,
                sourceAccountType = transfer.sourceAccountType.name,
                sourceEntityId = transfer.sourceEntityId,
                status = transfer.status.name,
                paymentIntentId = paymentIntentId,
                publicPaymentIntentId = publicPaymentIntentId
            )
        }
    }
}
