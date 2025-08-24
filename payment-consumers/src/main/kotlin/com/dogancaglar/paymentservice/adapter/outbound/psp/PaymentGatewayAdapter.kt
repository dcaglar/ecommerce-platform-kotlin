package com.dogancaglar.paymentservice.adapter.outbound.psp

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.util.PSPStatusMapper
import com.dogancaglar.paymentservice.ports.outbound.PaymentGatewayPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.*
import kotlin.random.Random

@Component
class PaymentGatewayAdapter(
    private val simulator: NetworkSimulator,
    private val config: PspSimulationProperties,
    @Qualifier("paymentOrderPspPool") private val pspExecutor: ThreadPoolTaskExecutor,
    private val meterRegistry: MeterRegistry        // <--- add this
) : PaymentGatewayPort {

    private val pspQueueDelay = Timer.builder("psp_queue_delay")
        .publishPercentileHistogram()
        .register(meterRegistry)

    private val pspExecDuration = Timer.builder("psp_exec_duration")
        .publishPercentileHistogram()
        .register(meterRegistry)

    private val logger = LoggerFactory.getLogger(javaClass)

    private val active: PspSimulationProperties.ScenarioConfig
        get() = config.scenarios[config.scenario]
            ?: throw IllegalStateException("No scenario config for ${config.scenario}")

    /** Public API: caller is a Kafka listener thread. Keep it clean. */
    override fun charge(order: PaymentOrder): PaymentOrderStatus {
        var causeLabel = "EXCEPTION"
        var future: Future<PaymentOrderStatus>? = null
        val enqueuedAt = System.nanoTime()

        return try {
            future = pspExecutor.submit<PaymentOrderStatus>
            {
                val startedAt = System.nanoTime()
                pspQueueDelay.record(startedAt - enqueuedAt, TimeUnit.NANOSECONDS)
                val t0 = System.nanoTime()
                try {
                    doCharge(order)
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
            return PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        } catch (e: InterruptedException) {
            // Listener thread was interrupted while waiting. We are continuing the listener,
            // so CLEAR the flag to avoid poisoning Kafka client paths.
            future?.cancel(true)
            logger.warn("Listener interrupted while waiting PSP result; mapping to transient timeout")
            Thread.interrupted()                // clear flag on listener thread
            causeLabel = "INTERRUPTED"
            return PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        } catch (e: CancellationException) {
            logger.warn("PSP future cancelled; mapping to transient timeout")
            causeLabel = "CANCELLED"
            return PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        } catch (e: ExecutionException) {
            if (e.cause is InterruptedException) {
                logger.warn("Worker interrupted; mapping to transient timeout")
                causeLabel = "WORKER_INTERRUPTED"
                return PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
            }
            logger.error("PSP worker failed: {}", e.cause?.message ?: e.message)
            causeLabel = "EXCEPTION"
            throw e
        } catch (e: RejectedExecutionException) {
            // Thrown by submit(...) when pool/queue are saturated (AbortPolicy)
            logger.warn("PSP executor saturated; treating as transient: {}", e.message)
            causeLabel = "REJECTED"
            return PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        } finally {
            meterRegistry.counter("psp_calls_total", "result", causeLabel).increment()
        }
    }

    /** Actual PSP work runs on the pool worker thread. */
    private fun doCharge(order: PaymentOrder): PaymentOrderStatus {
        // If this thread gets interrupted (e.g., due to cancel(true)),
        // any blocking/interruptible call below will throw InterruptedException.
        try {
            simulator.simulate()
        } catch (ie: InterruptedException) {
            meterRegistry.counter("psp_worker_interrupts_total").increment()
            // DO NOT re-interrupt here; just propagate.
            throw ie
        }

        val pspResponse = getPaymentResult()
        return PSPStatusMapper.fromPspStatus(pspResponse.status)
    }

    override fun chargeRetry(order: PaymentOrder): PaymentOrderStatus {
        simulator.simulate()
        val pspResponse = getRetryPaymentResult()
        return PSPStatusMapper.fromPspStatus(pspResponse.status)
    }

    override fun checkPaymentStatus(paymentOrderId: String): PaymentOrderStatus {
        simulator.simulate()
        val pspResponse = getPaymentStatusResult()
        return PSPStatusMapper.fromPspStatus(pspResponse.status)
    }

    private fun getPaymentResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < active.response.successful -> PaymentOrderStatus.SUCCESSFUL_FINAL
            roll < active.response.successful + active.response.retryable -> PaymentOrderStatus.FAILED_TRANSIENT_ERROR
            roll < active.response.successful + active.response.retryable + active.response.nonRetryable -> PaymentOrderStatus.DECLINED_FINAL
            else -> PaymentOrderStatus.PENDING_STATUS_CHECK_LATER
        }
        return PSPResponse(result.toString())
    }

    private fun getRetryPaymentResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < active.response.successful -> PaymentOrderStatus.SUCCESSFUL_FINAL
            roll < active.response.successful + active.response.retryable -> PaymentOrderStatus.FAILED_TRANSIENT_ERROR
            roll < active.response.successful + active.response.retryable + active.response.nonRetryable -> PaymentOrderStatus.DECLINED_FINAL
            else -> PaymentOrderStatus.PENDING_STATUS_CHECK_LATER
        }
        return PSPResponse(result.toString())
    }

    private fun getPaymentStatusResult(): PSPResponse {
        val roll = Random.nextInt(100)
        val result = when {
            roll < 50 -> PaymentOrderStatus.CAPTURE_PENDING_STATUS_CHECK_LATER
            roll < 70 -> PaymentOrderStatus.SUCCESSFUL_FINAL
            else -> PaymentOrderStatus.DECLINED_FINAL
        }
        return PSPResponse(result.toString())
    }
}