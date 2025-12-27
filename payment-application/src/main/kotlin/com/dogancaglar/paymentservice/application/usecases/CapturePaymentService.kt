package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.commands.CapturePaymentCommand
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.ports.inbound.CapturePaymentUseCase
import com.dogancaglar.paymentservice.ports.outbound.*
import org.slf4j.LoggerFactory


class CapturePaymentService(
    private val paymentRepository: PaymentRepository,
    private val psp: PspAuthorizationGatewayPort,
    private val outboxEventPort: OutboxEventRepository,
    private val idGeneratorPort: IdGeneratorPort,
    private val serializationPort: SerializationPort,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper) : CapturePaymentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)


    override fun capture(cmd: CapturePaymentCommand): PaymentOrder? {
        // 1️⃣check if authorization exist as pre-validation, check if thehere is paymentorder exist with status INTIATED_PENDING or not if not return bad-request.
            //todo what validation are we gonna do here, are we gona check only if auth exist?

            // 2️⃣ Persist a OutboxEvent<PAymentORderCaptureCommand>, but how are we gonna check outbox event dowe ahve anought information
                //return pending result
        return null
    }
}