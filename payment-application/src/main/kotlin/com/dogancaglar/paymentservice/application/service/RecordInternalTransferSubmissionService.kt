package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.paymentservice.application.events.InternalTransferCommand
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.paymentservice.domain.model.ledger.TxStatus
import com.dogancaglar.paymentservice.domain.model.payment.InternalTransfer
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.payment.PaymentSplit
import com.dogancaglar.paymentservice.domain.model.vo.InternalTransferId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId
import com.dogancaglar.paymentservice.ports.inbound.usecases.RecordInternalTransferSubmissionUseCase
import com.dogancaglar.paymentservice.ports.outbound.CentralDbTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import org.slf4j.LoggerFactory

class RecordInternalTransferSubmissionService(
    private val centralDbTransactionalFacadePort: CentralDbTransactionalFacadePort,
    private val idGeneratorPort: IdGeneratorPort,
    private val serializationPort: SerializationPort
) : RecordInternalTransferSubmissionUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun recordSubmission(
        paymentId: PaymentId,
        paymentIntentId: PaymentIntentId,
        publicPaymentIntentId: String,
        captureTxId: TxId,
        sourceAccount: String,
        targetAccount: String,
        journalType: JournalType,
        transferAmount : Amount
    ) {
        val transferId = InternalTransferId(idGeneratorPort.generateId()) // using same generator for simplicity

        // 1. Create InternalTransfer and mark as SENT_FOR_TRANSFER
        val internalTransfer = InternalTransfer.createNew(
            transferId = transferId,
            sourceTransactionId = captureTxId,
            amount = transferAmount,
            sourceAccount = sourceAccount,
            targetAccount = targetAccount,
            transferType =journalType.name
        ).markSentForTransfer()

        // 2. Create InternalTransferTx PENDING
        val txId = TxId(idGeneratorPort.generateId())
        val internalTransferTx = Tx.createInternalTransferTx(
            txId = txId,
            paymentId = paymentId,
            paymentIntentId = paymentIntentId,
            parentCaptureTxId = captureTxId,
            amount = transferAmount,
            sourceAccount = sourceAccount,
            targetAccount = targetAccount,
            txType = journalType,
            status = TxStatus.PENDING
        )

        // 3. Create EventEnvelope for Outbox
        val command = InternalTransferCommand.from(
            transfer = internalTransfer,
            txId = txId.value,
            paymentIntentId = paymentIntentId.value.toString(),
            publicPaymentIntentId = publicPaymentIntentId,
            journalType = journalType.name,
        )
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = command,
            aggregateId = publicPaymentIntentId,
            traceId = com.dogancaglar.common.logging.EventLogContext.getTraceId(),
            parentEventId = com.dogancaglar.common.logging.EventLogContext.getEventId()
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = idGeneratorPort.generateId(),
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = serializationPort.toJson(envelope)
        )

        // 4. Atomically persist
        logger.debug("Persisting InternalTransfer and Tx for paymentIntentId=$paymentIntentId, transferId=${transferId.value}")
        centralDbTransactionalFacadePort.recordInternalTransferOperationInLedger(
            internalTransfer = internalTransfer,
            tx = internalTransferTx,
            outboxEvents = listOf(outboxEvent),
            journalEntries = emptyList()
        )
    }
}
