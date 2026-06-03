package com.dogancaglar.paymentservice.infra.adapter.outbound.psp

import com.dogancaglar.paymentservice.domain.exception.PspPermanentException
import com.dogancaglar.paymentservice.domain.exception.PspTransientException
import com.dogancaglar.paymentservice.domain.model.payment.Payment
import com.dogancaglar.paymentservice.domain.model.payment.PspCaptureGatewayResponse
import com.dogancaglar.paymentservice.domain.model.payment.PspModificationStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.outbound.PspCaptureGatewayPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.random.Random


@Component
@ConditionalOnProperty(name = ["psp.gateway.type"], havingValue = "SIMULATED")
class SimulatedPspCaptureGatewayAdapter(
    private val captureSimulator: CaptureNetworkSimulator,
    private val captureConfig: CaptureSimulationProperties,
    private val refundSimulator: RefundNetworkSimulator,
    private val refundConfig: RefundSimulationProperties,
    @param:Qualifier("pspExecutionPool") private val pspExecutor: ThreadPoolTaskExecutor,
    private val meterRegistry: MeterRegistry
) : PspCaptureGatewayPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val activeCapture: CaptureSimulationProperties.ScenarioConfig
        get() = captureConfig.scenarios[captureConfig.scenario]
            ?: throw IllegalStateException("No capture scenario config for ${captureConfig.scenario}")

    private val activeRefund: RefundSimulationProperties.ScenarioConfig
        get() = refundConfig.scenarios[refundConfig.scenario]
            ?: throw IllegalStateException("No refund scenario config for ${refundConfig.scenario}")

    override fun capture(payment: Payment): CompletableFuture<PspCaptureGatewayResponse> {
        return CompletableFuture.supplyAsync({
            val roll = Random.nextInt(100)
            val sc = activeCapture.response
            val generatedPspRef = "sim_cap_${UUID.randomUUID()}"

            when {
                roll < sc.successful -> {
                    meterRegistry.counter("psp_calls_total", "result", "SUCCESS").increment()
                    PspCaptureGatewayResponse(
                        pspReference = generatedPspRef,
                        status = PspModificationStatus.PENDING_CAPTURE
                    )
                }
                roll < sc.successful + sc.retryable -> {
                    meterRegistry.counter("psp_calls_total", "result", "RETRYABLE").increment()
                    throw PspTransientException("Simulated transient gateway network timeout", RuntimeException("capture network lag"))
                }
                else -> {
                    meterRegistry.counter("psp_calls_total", "result", "DECLINED").increment()
                    throw PspPermanentException("Simulated terminal capture rejection by card scheme", RuntimeException("capture declined"))
                }
            }
        }, pspExecutor)
    }

    override fun refund(paymentIntentId: PaymentIntentId): CompletableFuture<PspModificationStatus> {
        return CompletableFuture.supplyAsync({
            val roll = Random.nextInt(100)
            val sc = activeRefund.response

            val resultStatus = when {
                roll < sc.successful -> PspModificationStatus.REFUNDED
                roll < sc.successful + sc.retryable -> PspModificationStatus.PENDING_REFUND
                else -> PspModificationStatus.REFUND_DECLINED_FINAL
            }

            meterRegistry.counter("psp_refund_calls_total", "result", resultStatus.name).increment()
            resultStatus
        }, pspExecutor)
    }
}