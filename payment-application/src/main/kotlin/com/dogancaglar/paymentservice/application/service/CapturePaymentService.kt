package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.paymentservice.application.command.CapturePaymentCommand
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder
import com.dogancaglar.paymentservice.ports.inbound.usecases.CapturePaymentUseCase
import com.dogancaglar.paymentservice.ports.outbound.*
import org.slf4j.LoggerFactory


class CapturePaymentService(
    private val psp: PspAuthorizationGatewayPort,
    private val outboxEventPort: LocalOutboxWriterPort,
    private val idGeneratorPort: IdGeneratorPort,
    private val serializationPort: SerializationPort) : CapturePaymentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)


    override fun capture(cmd: CapturePaymentCommand): PaymentOrder? {
        // 1️⃣check if authorization exist as pre-validation, check if thehere is paymentorder exist with status INTIATED_PENDING or not if not return bad-request.
            //todo what validation are we gonna do here, are we gona check only if auth exist?

            // 2️⃣ Persist a OutboxEvent<PAymentORderCaptureCommand>, but how are we gonna check outbox event dowe ahve anought information
                //return pending result
        return null
    }
}