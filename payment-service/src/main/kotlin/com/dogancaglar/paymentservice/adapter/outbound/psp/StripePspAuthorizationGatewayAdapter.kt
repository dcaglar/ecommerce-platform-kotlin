package com.dogancaglar.paymentservice.adapter.outbound.psp

import com.dogancaglar.paymentservice.domain.exception.PspPermanentException
import com.dogancaglar.paymentservice.domain.exception.PspTransientException
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.PaymentMethod
import com.dogancaglar.paymentservice.domain.util.PSPAuthorizationStatusMapper
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import com.stripe.StripeClient
import com.stripe.exception.ApiConnectionException
import com.stripe.exception.ApiException
import com.stripe.exception.RateLimitException
import com.stripe.exception.StripeException
import com.stripe.net.RequestOptions
import com.stripe.param.PaymentIntentConfirmParams
import com.stripe.param.PaymentIntentCreateParams
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.*
import kotlin.random.Random

@Component
class StripePspAuthorizationGatewayAdapter(
    private val stripeClient: StripeClient,
    private val simulator: AuthorizationNetworkSimulator,
    private val config: AuthorizationSimulationProperties,
    @Qualifier("pspAuthExecutor") private val pspAuthExecutor: ThreadPoolTaskExecutor,
    private val meterRegistry: MeterRegistry
) : PspAuthorizationGatewayPort {

    private val pspQueueDelay = Timer.builder("psp_queue_delay")
        .publishPercentileHistogram()
        .register(meterRegistry)

    private val pspExecDuration = Timer.builder("psp_exec_duration")
        .publishPercentileHistogram()
        .register(meterRegistry)

    private val logger = LoggerFactory.getLogger(javaClass)

    private val active: AuthorizationSimulationProperties.ScenarioConfig
        get() = config.scenarios[config.scenario]
            ?: throw IllegalStateException("No scenario config for ${config.scenario}")

    override fun authorize(idempotencyKey: String, paymentIntent: PaymentIntent, token: PaymentMethod?): PaymentIntentStatus {
        var causeLabel = "EXCEPTION"
        var future: Future<PaymentIntentStatus>? = null
        val enqueuedAt = System.nanoTime()

        return try {
            future = pspAuthExecutor.submit<PaymentIntentStatus> {
                val startedAt = System.nanoTime()
                pspQueueDelay.record(startedAt - enqueuedAt, TimeUnit.NANOSECONDS)

                val t0 = System.nanoTime()
                try {
                    doAuth(idempotencyKey, paymentIntent, token)
                } finally {
                    pspExecDuration.record(System.nanoTime() - t0, TimeUnit.NANOSECONDS)
                }
            }

            val status = future.get(500, TimeUnit.MILLISECONDS)
            causeLabel = status.name
            status

        } catch (t: TimeoutException) {
            future?.cancel(true)
            logger.warn("PSP call timed out (>500ms). paymentIntentId={}", paymentIntent.paymentIntentId.value)
            causeLabel = "TIMEOUT"
            PaymentIntentStatus.PENDING_AUTH

        } catch (e: InterruptedException) {
            future?.cancel(true)
            logger.warn("Listener interrupted while waiting PSP result; mapping to PENDING_AUTH")
            Thread.interrupted() // clear
            causeLabel = "INTERRUPTED"
            PaymentIntentStatus.PENDING_AUTH

        } catch (e: CancellationException) {
            logger.warn("PSP future cancelled; mapping to PENDING_AUTH")
            causeLabel = "CANCELLED"
            PaymentIntentStatus.PENDING_AUTH

        } catch (e: ExecutionException) {
            if (e.cause is InterruptedException) {
                logger.warn("Worker interrupted; mapping to PENDING_AUTH")
                causeLabel = "WORKER_INTERRUPTED"
                return PaymentIntentStatus.PENDING_AUTH
            }
            logger.error(
                "PSP worker failed for paymentIntentId={}: {}",
                paymentIntent.paymentIntentId.value,
                e.cause?.message ?: e.message,
                e.cause ?: e
            )
            causeLabel = "EXCEPTION"
            throw e

        } catch (e: RejectedExecutionException) {
            logger.warn("PSP executor saturated; treating as transient: {}", e.message)
            causeLabel = "REJECTED"
            PaymentIntentStatus.PENDING_AUTH

        } finally {
            meterRegistry.counter("psp_calls_total", "result", causeLabel).increment()
        }
    }

    private fun doAuth(idempotencyKey: String, paymentIntent: PaymentIntent, paymentMethod: PaymentMethod?): PaymentIntentStatus {
        return try {

            // ✅ Confirm uses the Stripe PI id + its own idempotency key
            val status = confirmIntent(idempotencyKey, paymentIntent.pspReference!!, paymentMethod)

            logger.info(
                "Stripe authorize done: paymentIntentId={}, pspReference={}, status={}",
                paymentIntent.paymentIntentId.value, paymentIntent.pspReference!!, status
            )

            status

        } catch (e: StripeException) {
            when {
                isRetryableError(e) -> {
                    logger.error(
                        "Retryable Stripe error during auth. paymentIntentId={}, code={}, msg={}",
                        paymentIntent.paymentIntentId.value, e.code, e.message, e
                    )
                    PaymentIntentStatus.PENDING_AUTH
                }
                else -> {
                    logger.error(
                        "Non-retryable Stripe error during auth. paymentIntentId={}, code={}, msg={}",
                        paymentIntent.paymentIntentId.value, e.code, e.message, e
                    )
                    PaymentIntentStatus.DECLINED
                }
            }
        } catch (e: Exception) {
            logger.error(
                "Unexpected error during Stripe auth. paymentIntentId={}, msg={}",
                paymentIntent.paymentIntentId.value, e.message, e
            )
            val pspResponse = getAuthorizationResponse()
            PSPAuthorizationStatusMapper.fromPspAuthCode(pspResponse.status)
        }
    }




    override fun createIntent(idempotencyKey: String, intent: PaymentIntent): CompletableFuture<PaymentIntent> {
        val  future =
            CompletableFuture.supplyAsync({
                try {
                    val params = PaymentIntentCreateParams.builder()
                        .setAmount(intent.totalAmount.quantity)
                        .setCurrency(intent.totalAmount.currency.currencyCode.lowercase())
                        .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                        .putMetadata("payment_intent_id", intent.paymentIntentId.value.toString())
                        .putMetadata("order_id", intent.orderId.value)
                        .putMetadata("buyer_id", intent.buyerId.value)
                        .build()

                    val opts = RequestOptions.builder()
                        .setIdempotencyKey(idempotencyKey)
                        .setMaxNetworkRetries(2)
                        .build()
                    val stripePaymentIntent = stripeClient.v1().paymentIntents().create(params, opts)

                    logger.info(
                        "Stripe PI created: paymentIntentId={}, pspReference={}, status={}",
                        intent.paymentIntentId.value, stripePaymentIntent.id, stripePaymentIntent.status
                    )
                    val createdPaymentIntent = intent.markAsCreatedWithPspReferenceAndClientSecret(pspReference = stripePaymentIntent.id, clientSecret = stripePaymentIntent.clientSecret)
                    createdPaymentIntent
                } catch (e: StripeException) {
                    if (isRetryableError(e)) throw PspTransientException("Stripe create transient failure", e)
                    throw PspPermanentException("Stripe create permanent failure", e)
                }
            }, pspAuthExecutor)
         return  future

}

    private fun isRetryableError(e: StripeException): Boolean {
        return when (e) {
            is ApiConnectionException -> true
            is ApiException -> e.statusCode in 500..599
            is RateLimitException -> true
            else -> false
        }
    }





    override fun confirmIntent(idempotencyKey: String, pspReference: String, token: PaymentMethod?): PaymentIntentStatus {
        // Build confirm params - only set paymentMethod if provided
        // For Stripe Payment Element, payment method is already attached to PaymentIntent
        val paramsBuilder = PaymentIntentConfirmParams.builder()
        
        token?.let { paymentMethod ->
            val paymentMethodId = when (paymentMethod) {
                is PaymentMethod.CardToken -> paymentMethod.token // assume pm_...
            }
            paramsBuilder.setPaymentMethod(paymentMethodId)
            logger.info("Using provided payment method: {}", paymentMethodId)
        } ?: run {
            logger.info("No payment method provided - using payment method already attached to PaymentIntent")
        }

        val params = paramsBuilder.build()

        val opts = RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .build()

        val confirmed = stripeClient.v1().paymentIntents().confirm(pspReference, params, opts)

        logger.info(
            "Stripe PI confirmed: pspReference={}, status={}",
            pspReference, confirmed.status
        )

        return mapStripeStatusToDomainStatus(confirmed.status)
    }

    private fun mapStripeStatusToDomainStatus(stripeStatus: String?): PaymentIntentStatus =
        when (stripeStatus?.uppercase()) {
            "REQUIRES_CAPTURE", "SUCCEEDED" -> PaymentIntentStatus.AUTHORIZED
            "CANCELED", "CANCELLED" -> PaymentIntentStatus.CANCELLED
            "PROCESSING" -> PaymentIntentStatus.PENDING_AUTH
            "REQUIRES_ACTION", "REQUIRES_CONFIRMATION", "REQUIRES_PAYMENT_METHOD" -> PaymentIntentStatus.DECLINED
            else -> PaymentIntentStatus.DECLINED
        }

    override fun retrieveClientSecret(pspReference: String): String? {
        return try {
            val retrieved = stripeClient.v1().paymentIntents().retrieve(pspReference)
            logger.info(
                "Retrieved clientSecret from Stripe: pspReference={}, status={}",
                pspReference, retrieved.status
            )
            retrieved.clientSecret
        } catch (e: Exception) {
            logger.error("Failed to retrieve clientSecret from Stripe: pspReference={}, error={}", pspReference, e.message, e)
            null
        }
    }

    private fun getAuthorizationResponse(): AuthorizationPspResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < active.response.successful -> "AUTHORIZED"
            roll < active.response.successful + active.response.retryable -> "TRANSIENT_NETWORK_ERROR"
            roll < active.response.successful + active.response.retryable + active.response.nonRetryable -> "DECLINED"
            else -> "PENDING_AUTH"
        }
        return AuthorizationPspResponse(result)
    }
}