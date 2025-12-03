package com.dogancaglar.paymentservice.adapter.outbound.psp

import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.util.PSPAuthorizationStatusMapper
import com.dogancaglar.paymentservice.ports.outbound.PspAuthGatewayPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.*
import kotlin.random.Random

@Component
class PspAuthorizationGatewayAdapter(
    private val simulator: AuthorizationNetworkSimulator,
    private val config: AuthorizationSimulationProperties,
    @Qualifier("pspAuthExecutor") private val pspAuthExecutor: ThreadPoolTaskExecutor,
    private val meterRegistry: MeterRegistry        // <--- add this
) : PspAuthGatewayPort {
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

    /** Public API: caller is a Kafka listener thread. Keep it clean. */
    override fun authorize(order: Payment): PaymentStatus {
        var causeLabel = "EXCEPTION"
        var future: Future<PaymentStatus>? = null
        val enqueuedAt = System.nanoTime()

        return try {
            future = pspAuthExecutor.submit<PaymentStatus>
            {
                val startedAt = System.nanoTime()
                pspQueueDelay.record(startedAt - enqueuedAt, TimeUnit.NANOSECONDS)
                val t0 = System.nanoTime()
                try {
                    //use your unique paymetid as idempotency key.
                    doAuth(order.paymentId.toPublicPaymentId())
                } finally {
                    pspExecDuration.record(System.nanoTime() - t0, TimeUnit.NANOSECONDS)
                }
            }
            // Wait up to 1s for PSP result
            val status = future.get(500, TimeUnit.MILLISECONDS)
            causeLabel = status.name
            status
        } catch (t: TimeoutException) {
            // Time limit exceeded → cancel and map to transient timeout
            future?.cancel(true)                // interrupts worker
            logger.warn("PSP call timed out (>{}s)", 1)
            causeLabel = "TIMEOUT"
            return PaymentStatus.PENDING_AUTH
        } catch (e: InterruptedException) {
            // Listener thread was interrupted while waiting. We are continuing the listener,
            // so CLEAR the flag to avoid poisoning Kafka client paths.
            future?.cancel(true)
            logger.warn("Listener interrupted while waiting PSP result; mapping to transient timeout")
            Thread.interrupted()                // clear flag on listener thread
            causeLabel = "INTERRUPTED"
            return PaymentStatus.PENDING_AUTH
        } catch (e: CancellationException) {
            logger.warn("PSP future cancelled; mapping to transient timeout")
            causeLabel = "CANCELLED"
            return PaymentStatus.PENDING_AUTH
        } catch (e: ExecutionException) {
            if (e.cause is InterruptedException) {
                logger.warn("Worker interrupted; mapping to transient timeout")
                causeLabel = "WORKER_INTERRUPTED"
                return PaymentStatus.PENDING_AUTH
            }
            logger.error("PSP worker failed: {}", e.cause?.message ?: e.message)
            causeLabel = "EXCEPTION"
            throw e
        } catch (e: RejectedExecutionException) {
            // Thrown by submit(...) when pool/queue are saturated (AbortPolicy)
            logger.warn("PSP executor saturated; treating as transient: {}", e.message)
            causeLabel = "REJECTED"
            return PaymentStatus.PENDING_AUTH
        } finally {
            meterRegistry.counter("psp_calls_total", "result", causeLabel).increment()
        }
    }
    /** Actual PSP work runs on the pool worker thread. */
    private fun doAuth(idempotencyKey: String): PaymentStatus {
        // If this thread gets interrupted (e.g., due to cancel(true)),
        // any blocking/interruptible call below will throw InterruptedException.
        try {
            simulator.simulate()
        } catch (ie: InterruptedException) {
            meterRegistry.counter("psp_worker_interrupts_total").increment()
            // DO NOT re-interrupt here; just propagate.
            throw ie
        }

        val pspResponse = getAuthoriationResponse()
        return PSPAuthorizationStatusMapper.fromPspAuthCode  (pspResponse.status)
    }

    private fun getAuthoriationResponse(): AuthorizationPspResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < active.response.successful -> "AUTHORIZED"
            roll < active.response.successful + active.response.retryable -> "TRANSIENT_NETWORK_ERROR"
            roll < active.response.successful + active.response.retryable + active.response.nonRetryable -> "DECLINED"
            else -> PaymentStatus.PENDING_AUTH
        }
        return AuthorizationPspResponse(result.toString())
    }

}