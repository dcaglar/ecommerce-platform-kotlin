package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.commands.GetPaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.ports.inbound.GetPaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import org.slf4j.LoggerFactory

class GetPaymentIntentService(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val pspAuthGatewayPort: PspAuthorizationGatewayPort
) : GetPaymentIntentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun getPaymentIntent(cmd: GetPaymentIntentCommand): PaymentIntent {
        // Load PaymentIntent from database
        val paymentIntent = paymentIntentRepository.findById(cmd.paymentIntentId)
            ?: error("PaymentIntent ${cmd.paymentIntentId.value} not found")

        // If pspReference exists, retrieve clientSecret from Stripe (never persisted)
        return if (paymentIntent.hasPspReference()) {
            val clientSecret = pspAuthGatewayPort.retrieveClientSecret(paymentIntent.pspReferenceOrThrow())
            if (clientSecret != null) {
                logger.info("Retrieved clientSecret from Stripe for paymentIntentId={}", cmd.paymentIntentId.value)
                // Update PaymentIntent with clientSecret (in-memory only, not persisted)
                paymentIntent.withClientSecret(clientSecret)
            } else {
                logger.warn("Could not retrieve clientSecret from Stripe for pspReference={}", paymentIntent.pspReference)
                paymentIntent
            }
        } else {
            logger.debug("No pspReference found for paymentIntentId={}, skipping Stripe retrieval", cmd.paymentIntentId.value)
            paymentIntent
        }
    }
}

