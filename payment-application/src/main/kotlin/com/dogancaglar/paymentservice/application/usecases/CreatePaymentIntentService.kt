package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspAuthGatewayPort
import org.slf4j.LoggerFactory
import java.time.Instant

class CreatePaymentIntentService(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val idGeneratorPort: IdGeneratorPort,
    private val psp: PspAuthGatewayPort
) : CreatePaymentIntentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun create(cmd: CreatePaymentIntentCommand): PaymentIntent {
        // 1. Generate ID and create PaymentIntent
        val paymentIntentId = PaymentIntentId(idGeneratorPort.nextPaymentIntentId(cmd.buyerId, cmd.orderId))
        val paymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = cmd.buyerId,
            orderId = cmd.orderId,
            totalAmount = cmd.totalAmount,
            paymentOrderLines = cmd.paymentOrderLines
        )

        // 2. Save to database
        paymentIntentRepository.save(paymentIntent)
        logger.info("Saved PaymentIntent: {}", paymentIntentId.value)

        // 3. Create in Stripe
        val stripeKey = "create:${paymentIntentId.value}"
        val updatedPaymentIntent = psp.createIntent(stripeKey, paymentIntent)
        logger.info("Created in Stripe: {}", updatedPaymentIntent.pspReference)

        // 4. Update pspReference (best effort,do nmot store secret)
        updatePspReferenceSafely(updatedPaymentIntent)

        // 5. Retrieve clientSecret from Stripe for response (never stored - compliance requirement)
        return updatedPaymentIntent
    }

    private fun updatePspReferenceSafely(paymentIntent: PaymentIntent) {
        try {
            paymentIntentRepository.updatePspReference(
                paymentIntent.paymentIntentId.value,
                paymentIntent.pspReferenceOrThrow(),
                Instant.now()
            )
            logger.info("Updated pspReference: {}", paymentIntent.pspReferenceOrThrow())
        } catch (ex: Exception) {
            logger.warn("Unexpected error updating pspReference: {}", ex.message)
        }
    }
}
