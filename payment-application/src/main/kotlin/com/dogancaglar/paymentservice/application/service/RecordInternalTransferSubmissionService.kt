package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.paymentservice.application.events.EventType
import com.dogancaglar.paymentservice.application.events.InternalTransferCommand
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
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
import org.springframework.stereotype.Service

@Service
class RecordInternalTransferSubmissionService(
    private val centralDbTransactionalFacadePort: CentralDbTransactionalFacadePort,
    private val idGeneratorPort: IdGeneratorPort,
    private val eventEnvelopeFactory: EventEnvelopeFactory,
    private val serializationPort: SerializationPort
) : RecordInternalTransferSubmissionUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun recordSubmission(
        paymentId: PaymentId,
        paymentIntentId: PaymentIntentId,
        publicPaymentIntentId: String,
        captureTxId: TxId,
        sourceAccountType: AccountType,
        sourceEntityId: String,
        split: PaymentSplit
    ) {
        val transferId = InternalTransferId(idGeneratorPort.generateId()) // using same generator for simplicity

        // 1. Create InternalTransfer and mark as SENT_FOR_TRANSFER
        val internalTransfer = InternalTransfer.createNew(
            transferId = transferId,
            sourceTransactionId = captureTxId,
            amount = split.amount,
            targetAccountType = split.targetAccountType,
            targetEntityId = split.targetEntityId,
            sourceAccountType = sourceAccountType,
            sourceEntityId = sourceEntityId
        ).markSentForTransfer()

        // 2. Create InternalTransferTx PENDING
        val txId = TxId(idGeneratorPort.generateId())
        val internalTransferTx = Tx.createInternalTransferTx(
            txId = txId,
            paymentId = paymentId,
            paymentIntentId = paymentIntentId,
            parentCaptureTxId = captureTxId,
            amount = split.amount,
            targetAccountType = split.targetAccountType,
            targetEntityId = split.targetEntityId,
            status = TxStatus.PENDING
        )

        // 3. Create EventEnvelope for Outbox
        val command = InternalTransferCommand.from(
            transfer = internalTransfer,
            txId = txId.value,
            paymentIntentId = paymentIntentId.value.toString(),
            publicPaymentIntentId = publicPaymentIntentId
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
        logger.info("Persisting InternalTransfer and Tx for paymentIntentId=$paymentIntentId, transferId=${transferId.value}")
        centralDbTransactionalFacadePort.recordInternalTransferOperationInLedger(
            internalTransfer = internalTransfer,
            tx = internalTransferTx,
            outboxEvents = listOf(outboxEvent)
        )
    }
}
