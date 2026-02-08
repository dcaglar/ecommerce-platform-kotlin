package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.exception.PspPermanentException
import com.dogancaglar.paymentservice.domain.exception.PspTransientException
import com.dogancaglar.paymentservice.domain.exception.PspUnknownException
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.ResilientExecutionPort
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class CreatePaymentIntentService(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val idGeneratorPort: IdGeneratorPort,
    private val pspAuthGatewayPort: PspAuthorizationGatewayPort,
    private val resilientExecutionPort: ResilientExecutionPort,
    private val pspCallbackExecutor: Executor
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
        val startSave = System.currentTimeMillis()
        paymentIntentRepository.save(paymentIntent)
        val finishSave = System.currentTimeMillis()
        logger.info("db.savePaymentIntent took {} ms", finishSave - startSave)
        logger.debug("Saved PaymentIntent: {}", paymentIntentId.value)

        // 3. Create async task to be executed(actual stripe call
        /*
        /*Returns a new CompletableFuture<PaymentIntent> that is asynchronously completed by a task( callCreatePaymentIntentApi(paymentIntent)) running in the given
         executor(pspAuthExecutor) with the value obtained by calling the given Supplier.
        */
         */
        // Wait with timeout
        return try {
            val startStripe = System.currentTimeMillis()
            val result = resilientExecutionPort.executeWithTimeoutAndBackgroundFallback(
                primaryTask = pspAuthGatewayPort.createPaymentIntent(paymentIntent), // tsk to be run aysnc by thread pool passed
                timeoutMs = 3000,
                onTimeoutFallback = {
                    logger.debug(
                        "Payment creation timed out for {}, continuing in background",
                        paymentIntent.paymentIntentId.value
                    )
                    paymentIntent // Returns CREATED_PENDING status, mapping to 202 in controller
                },
                onBackgroundSuccess = { resultFromCallStripeApi ->
                    handleBackgroundPaymentIntentCreationSuccess(
                        resultFromCallStripeApi
                    )
                },
                onBackgroundFailure = { error -> handleBackgroundFailure(paymentIntent, error) }
            )
            val finishStripe = System.currentTimeMillis()
            logger.info("Stripe createPaymentIntent took {} ms", finishStripe - startStripe)
            result
        } catch (e: Exception){
            handleImmediateFailure(paymentIntent,e)
        }
    }

    //a callable
    private fun handleBackgroundPaymentIntentCreationSuccess(successfulPaymentIntent: PaymentIntent) {
        logger.info("Background payment intent creation succesful for ${successfulPaymentIntent.paymentIntentId.value}, promooting to status created and psp reference from stripe:${successfulPaymentIntent.pspReferenceOrThrow()}")
        val startUpdate = System.currentTimeMillis()
        paymentIntentRepository.updatePaymentIntent(successfulPaymentIntent)
        val finishUpdate = System.currentTimeMillis()
        logger.info("db.updatePaymentIntent (success) took {} ms", finishUpdate - startUpdate)
    }


    private fun handleBackgroundFailure(paymentIntent: PaymentIntent, error: Throwable) {
        logger.error("Background payment creation failed for ${paymentIntent.paymentIntentId.value}", error)
        if (error is PspPermanentException || error !is PspTransientException) {
            //cancel if we get exception when it waits for stripe response, and if it is psp permanent
            val canceledPaymentIntent = paymentIntent.markCancelled()
            val startUpdate = System.currentTimeMillis()
            paymentIntentRepository.updatePaymentIntent(canceledPaymentIntent)
            val finishUpdate = System.currentTimeMillis()
            logger.info("db.updatePaymentIntent (failure) took {} ms", finishUpdate - startUpdate)
        }
    }

    private fun handleImmediateFailure(paymentIntent: PaymentIntent, error: Throwable): PaymentIntent {
        logger.error("handle imeatiate failure :" + error)
        val cause = if (error is CompletionException) error.cause ?: error else error
        return when (cause) {
            is PspTransientException -> {
                logger.warn("Transient failure during payment creation for {}: {}", paymentIntent.paymentIntentId.value, cause.message)
                paymentIntent
            }
            is RejectedExecutionException -> {
                logger.warn("PSP thread pool saturated for {}, returning pending status (backpressure)", paymentIntent.paymentIntentId.value)
                paymentIntent // Return CREATED_PENDING status, mapping to 202 in controller
            }
            is PspPermanentException -> {
                logger.error("Permanent failure during payment creation for {}: {}", paymentIntent.paymentIntentId.value, cause.message)
                val cancelled = paymentIntent.markCancelled()
                val startUpdate = System.currentTimeMillis()
                paymentIntentRepository.updatePaymentIntent(cancelled)
                val finishUpdate = System.currentTimeMillis()
                logger.info("db.updatePaymentIntent (immediate failure) took {} ms", finishUpdate - startUpdate)
                cancelled
            }
            else -> {
                logger.error("Unexpected failure during payment creation for {}", paymentIntent.paymentIntentId.value, cause)
                throw RuntimeException("Unexpected failure", cause)
            }
        }
    }

    private fun setupBackgroundCompletion(
        future: java.util.concurrent.CompletableFuture<PaymentIntent>,
        paymentIntent: PaymentIntent
    ) {
        future.whenCompleteAsync({ result, error ->
            try {
                if (error != null) {
                    val cause = if (error is java.util.concurrent.CompletionException) error.cause ?: error else error
                    logger.error("Background payment creation failed for ${paymentIntent.paymentIntentId.value}: ${cause.message}", cause)
                    
                    // Permanent failures must cancel the intent in our DB
                    if (cause is PspPermanentException || (cause !is PspTransientException && cause !is java.util.concurrent.TimeoutException)) {
                        paymentIntentRepository.updatePaymentIntent(paymentIntent.markCancelled())
                    }
                } else if (result != null) {
                    logger.debug("Background payment creation succeeded for {}", paymentIntent.paymentIntentId.value)
                    paymentIntentRepository.updatePaymentIntent(result)
                }
            } catch (e: Exception) {
                logger.error("Error in setupBackgroundCompletion for ${paymentIntent.paymentIntentId.value}", e)
            }
        }, pspCallbackExecutor)
    }
}
