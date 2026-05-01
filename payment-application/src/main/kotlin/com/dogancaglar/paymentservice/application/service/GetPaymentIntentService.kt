package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.paymentservice.application.command.GetPaymentIntentCommand
import com.dogancaglar.paymentservice.domain.exception.PaymentIntentNotFoundException
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.ports.inbound.usecases.GetPaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.ResilientExecutionPort
import org.slf4j.LoggerFactory

class GetPaymentIntentService(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val pspAuthGatewayPort: PspAuthorizationGatewayPort,
    private val resilientExecutionPort: ResilientExecutionPort
) : GetPaymentIntentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun getPaymentIntent(cmd: GetPaymentIntentCommand): PaymentIntent {
        // Load PaymentIntent from database
        val paymentIntent = paymentIntentRepository.findById(cmd.paymentIntentId)
            ?: throw PaymentIntentNotFoundException("PaymentIntent ${cmd.paymentIntentId.value} not found")

        // If pspReference exists, retrieve clientSecret from Stripe (never persisted)
        return if (paymentIntent.hasPspReference()) {
            val clientSecretFuture = pspAuthGatewayPort.retrieveClientSecret(paymentIntent.pspReferenceOrThrow())
            if (clientSecretFuture != null) {
                return try {
                    val clientSecret = resilientExecutionPort.executeWithTimeoutAndBackgroundFallback(
                        primaryTask = clientSecretFuture,
                        timeoutMs = 2000,
                        onTimeoutFallback = {
                            logger.warn("Timeout retrieving clientSecret from Stripe for pspReference={}", paymentIntent.pspReference)
                            ""
                        },
                        onBackgroundSuccess = { },
                        onBackgroundFailure = { }
                    )
                    
                    if (clientSecret.isNotEmpty()) {
                        logger.info("Retrieved clientSecret from Stripe for paymentIntentId={}", cmd.paymentIntentId.value)
                        paymentIntent.withClientSecret(clientSecret)
                    } else {
                        paymentIntent
                    }
                } catch (e: Exception) {
                    logger.error("Error retrieving client secret", e)
                    paymentIntent
                }
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

