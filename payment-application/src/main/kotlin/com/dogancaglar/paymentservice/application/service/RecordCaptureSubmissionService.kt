package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.CaptureConfirmed
import com.dogancaglar.paymentservice.application.events.CaptureSubmitted
import com.dogancaglar.paymentservice.ports.outbound.PspSimulationRulesPort
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.paymentservice.domain.model.ledger.TxStatus.PENDING
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId
import com.dogancaglar.paymentservice.ports.inbound.usecases.RecordCaptureSubmissionUseCase
import com.dogancaglar.paymentservice.ports.outbound.CentralDbTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import org.slf4j.LoggerFactory

open class RecordCaptureSubmissionService(
    private val centralDbTransactionalFacadePort: CentralDbTransactionalFacadePort,
    private val paymentRepository: PaymentRepository,
    private val paymentTxPort: PaymentTxPort,
    private val idGeneratorPort: IdGeneratorPort,
    private val serializationPort: SerializationPort,
    private val pspSimulationRulesPort: PspSimulationRulesPort
) : RecordCaptureSubmissionUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun recordSubmission(event: CaptureSubmitted, traceId: String, parentEventId: String) {
        val paymentIntentId = PaymentIntentId(event.paymentIntentId.toLongOrNull() ?: 0L)
        val payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
            ?: throw IllegalStateException("Payment context aggregate absent for paymentIntentId=${event.paymentIntentId}")

        // 1. Advance aggregate state mutations
        val updatedPayment = payment.markSentForSettle()

        // 2. Link parent Authorization transaction context
        val txs = paymentTxPort.findByPaymentId(payment.paymentId.value)
        val authTx = txs.find { it.txType == com.dogancaglar.paymentservice.domain.model.ledger.JournalType.AUTHORIZATION }
        val authTxIdValue = authTx?.txId ?: TxId(0L)

        // 3. Setup transaction tracking metadata record
        val newTxId = TxId(idGeneratorPort.generateId())
        val amount = Amount.of(event.amountValue, Currency(event.currency))

        val captureTx = Tx.createCaptureTx(
            txId = newTxId,
            paymentId = payment.paymentId,
            paymentIntentId = paymentIntentId,
            authorizationTxId = authTxIdValue,
            acquirerReference = event.pspReference,
            amount = amount,
            status = PENDING
        )

        //TODO simulation ,here also just create one Outbox<CaptureConfirmed> for simulator purposes.
        val outboxEvents = mutableListOf<OutboxEvent>()
        if (pspSimulationRulesPort.isSimulationTarget(event.merchantAccount)) {
            logger.debug("Simulation target profile verified for merchant=${event.merchantAccount}. Generating automatic Stage 2 loopback confirmation.")
            val captureConfirmed = CaptureConfirmed(
                paymentIntentId = event.paymentIntentId,
                publicPaymentIntentId = event.publicPaymentIntentId,
                merchantAccount = event.merchantAccount,
                amountValue = event.amountValue,
                currency = event.currency
            )

            val captureConfirmedEnvelope = EventEnvelopeFactory.envelopeFor(
                traceId = EventLogContext.getTraceId(),
                data = captureConfirmed,
                aggregateId = event.publicPaymentIntentId,
                parentEventId = EventLogContext.getEventId()
            )

            val captureConfirmedOutboxEvent = OutboxEvent.createNew(
                oeid = idGeneratorPort.generateId(),
                eventType = captureConfirmedEnvelope.eventType,
                aggregateId = captureConfirmedEnvelope.aggregateId,
                payload = serializationPort.toJson(captureConfirmedEnvelope)
            )
            outboxEvents.add(captureConfirmedOutboxEvent)
        }

        // 5. Commit atomic units through outbound database gateways
        logger.debug("Atomically persisting pending state modifications and transaction outbox event for track ref=${event.pspReference}")
        centralDbTransactionalFacadePort.recordPaymentOperationInLedger(updatedPayment, captureTx, emptyList(), outboxEvents)
    }
}