package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.CaptureSubmitted
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
import org.slf4j.LoggerFactory

open class RecordCaptureSubmissionService(
    private val centralDbTransactionalFacadePort: CentralDbTransactionalFacadePort,
    private val paymentRepository: PaymentRepository,
    private val paymentTxPort: PaymentTxPort,
    private val idGeneratorPort: IdGeneratorPort
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
        val authTx = txs.find { it.txType == "AUTHORIZATION" }
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


        /*
        also actually another thing currently the system behaves like default manual capture is needed, i mean that means merchant do have to send auth and capture seperately, but i do want actually defauk behavior  , so that when the payment is authorized once then we should simply also
         */
        // 5. Commit atomic units through outbound database gateways
        logger.info("Atomically persisting pending state modifications and transaction outbox event for track ref=${event.pspReference}")
        centralDbTransactionalFacadePort.recordPaymentOperationInLedger(updatedPayment, captureTx, emptyList(), emptyList())
    }
}