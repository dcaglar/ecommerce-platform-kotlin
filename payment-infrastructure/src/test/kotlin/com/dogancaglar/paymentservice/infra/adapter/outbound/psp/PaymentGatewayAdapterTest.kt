package com.dogancaglar.paymentservice.infra.adapter.outbound.psp

import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

class PaymentGatewayAdapterTest {

    private lateinit var networkSimulator: CaptureNetworkSimulator
    private lateinit var config: CaptureSimulationProperties
    private lateinit var pspExecutor: ThreadPoolTaskExecutor
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var adapter: PspModificationGatewayAdapter

    @BeforeEach
    fun setUp() {
        val config = CaptureSimulationProperties().apply {
            currentScenario = PspCaptureScenario.BEST_PSP_EVER.name
            scenarios = mapOf(
                PspCaptureScenario.BEST_PSP_EVER to CaptureSimulationProperties.ScenarioConfig().apply {
                    timeouts = CaptureSimulationProperties.TimeoutConfig().apply { enabled = false }
                    latency = CaptureSimulationProperties.LatencyConfig().apply {
                        fast = CaptureSimulationProperties.LatencyBucket().apply { probability = 100; minMs = 1; maxMs = 2 }
                    }
                    response = CaptureSimulationProperties.ResponseDistribution().apply {
                        successful = 100
                        retryable = 0
                        nonRetryable = 0
                    }
                }
            )
        }
        val refundConfig = RefundSimulationProperties().apply {
            currentScenario = PspRefundScenario.BEST_PSP_EVER.name
            scenarios = mapOf(
                PspRefundScenario.BEST_PSP_EVER to RefundSimulationProperties.ScenarioConfig().apply {
                    timeouts = RefundSimulationProperties.TimeoutConfig().apply { enabled = false }
                    latency = RefundSimulationProperties.LatencyConfig().apply {
                        fast = RefundSimulationProperties.LatencyBucket().apply { probability = 100; minMs = 1; maxMs = 2 }
                    }
                    response = RefundSimulationProperties.ResponseDistribution().apply {
                        successful = 100
                        retryable = 0
                        nonRetryable = 0
                    }
                }
            )
        }
        val simulator = CaptureNetworkSimulator(config)
        val refundSimulator = RefundNetworkSimulator(refundConfig)

        val executor = ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 2
            queueCapacity = 10
            initialize()
        }

        adapter = PspModificationGatewayAdapter(simulator, config, refundSimulator, refundConfig, executor, meterRegistry)
    }
}
