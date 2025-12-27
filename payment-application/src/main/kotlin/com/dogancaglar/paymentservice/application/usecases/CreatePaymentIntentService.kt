package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.exception.PspPermanentException
import com.dogancaglar.paymentservice.domain.exception.PspTransientException
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class CreatePaymentIntentService(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val idGeneratorPort: IdGeneratorPort,
    private val pspAuthGatewayPort: PspAuthorizationGatewayPort,
    private val pspCallbackExecutor: ThreadPoolTaskExecutor
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
        val updatedPaymentIntentFuture = pspAuthGatewayPort.createIntent(stripeKey, paymentIntent)
        try {
            val result = updatedPaymentIntentFuture.get(3000, TimeUnit.MILLISECONDS)
            paymentIntentRepository.updatePaymentIntent(result)
            return result
        } catch (e: TimeoutException) {
            // 5. Handle timeout - continue in background
            logger.info("Payment creation timed out, continuing in background")
            // Set up completion handler for the background task
            setupBackgroundCompletion(updatedPaymentIntentFuture, paymentIntent)
            return paymentIntent

        } catch (e: ExecutionException) {
            val cause = (e.cause as? CompletionException)?.cause ?: e.cause
            when (cause) {
                is PspTransientException -> {/* schedule retry */
                }

                is PspPermanentException -> {/* mark failed */
                    // Mark as permanently failed
                    val declined = paymentIntent.markDeclined()
                    paymentIntentRepository.updatePaymentIntent(declined)
                    return declined
                }

                else -> throw RuntimeException("Unexpected failure", cause)
            }
            // 5. Retrieve clientSecret from Stripe for response (never stored - compliance requirement)
            return paymentIntent
        }
    }



        private fun setupBackgroundCompletion(
            future: CompletableFuture<PaymentIntent>,
            paymentIntent: PaymentIntent
        ) {
            // Handle the completion of the future even after timeout
            future.whenCompleteAsync( { result, error ->
                try {
                    when {
                        // Success case
                        result != null -> {
                            logger.info("Background payment creation succeeded for ${paymentIntent.paymentIntentId.value}")
                            paymentIntentRepository.updatePaymentIntent(result)
                        }

                        // Error case
                        error != null -> {
                            logger.error("Background payment creation failed for ${paymentIntent.paymentIntentId.value}", error)
                            val cause = if (error is CompletionException) error.cause else error

                            if (cause is PspTransientException) {
                                //schedule for later on
                            } else {
                                //fail permanaetly.
                                paymentIntentRepository.updatePaymentIntent(paymentIntent.markDeclined())
                            }
                        }

                        // This shouldn't happen
                        else -> {
                            logger.error("Both result and error are null for ${paymentIntent.paymentIntentId.value}")
                            //scheduykle for later
                        }
                    }
                } catch (e: Exception) {
                    //scheduykle for later
                    logger.error("Error processing background completion for ${paymentIntent.paymentIntentId.value}", e)
                }
            },pspCallbackExecutor)
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
