package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.commands.ProcessPaymentIntentUpdateCommand
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.ports.inbound.ProcessPaymentIntentUpdateUseCase
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import org.slf4j.LoggerFactory

class ProcessPaymentIntentUpdateService(
    private val paymentIntentRepository: PaymentIntentRepository
) : ProcessPaymentIntentUpdateUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun processUpdate(cmd: ProcessPaymentIntentUpdateCommand): PaymentIntent {

        logger.info("Processing payment intent update: id={}, status={}", cmd.paymentIntentId.value, cmd.status)

        val paymentIntent = paymentIntentRepository.findById(cmd.paymentIntentId)

        return when (paymentIntent.status) {
            PaymentIntentStatus.CREATED_PENDING -> {
                if (cmd.status == PaymentIntentStatus.CREATED) {
                    val updated = paymentIntent.markAsCreatedWithPspReferenceAndClientSecret(
                        pspReference = cmd.pspReference,
                        clientSecret = cmd.clientSecret ?: ""
                    )
                    paymentIntentRepository.updatePaymentIntent(updated)
                    logger.info("Updated PaymentIntent to CREATED: {}", cmd.paymentIntentId.value)
                    updated
                } else {
                    logger.warn("Ignoring invalid transition from CREATED_PENDING to {}", cmd.status)
                    paymentIntent
                }
            }
            PaymentIntentStatus.CREATED -> {
                // If we receive another CREATED event, just return existing (idempotency)
                if (cmd.status == PaymentIntentStatus.CREATED) {
                    logger.info("PaymentIntent already CREATED: {}", cmd.paymentIntentId.value)
                    paymentIntent
                } else {
                    // Handle other transitions if needed (e.g. directly to AUTHORIZED? unlikely without PENDING_AUTH)
                    logger.warn("Ignoring transition from CREATED to {}", cmd.status)
                    paymentIntent
                }
            }
            else -> {
                logger.info("PaymentIntent already in state {}, ignoring update to {}", paymentIntent.status, cmd.status)
                paymentIntent
            }
        }
    }
}