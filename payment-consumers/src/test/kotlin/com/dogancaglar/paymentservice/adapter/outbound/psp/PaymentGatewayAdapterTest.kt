package com.dogancaglar.paymentservice.adapter.outbound.psp

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

class PaymentGatewayAdapterTest {

    private lateinit var networkSimulator: CaptureNetworkSimulator
    private lateinit var config: CaptureSimulationProperties
    private lateinit var pspExecutor: ThreadPoolTaskExecutor
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var clock: Clock
    private lateinit var adapter: PspCaptureGatewayAdapter

    @BeforeEach
    fun setUp() {
        networkSimulator = mockk()
        config = mockk()
        pspExecutor = mockk()
        meterRegistry = mockk(relaxed = true)
        clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)

        // Mock MeterRegistry behavior
        val counter = mockk<Counter>(relaxed = true)
        every { meterRegistry.counter(any<String>(), any<String>(), any<String>()) } returns counter
        every { meterRegistry.timer(any<String>()) } returns mockk<Timer>()
        
        // Mock CaptureNetworkSimulator behavior
        every { networkSimulator.simulate() } returns Unit
        
        // Mock ThreadPoolTaskExecutor behavior - use relaxed mocking to avoid signature issues
        every { pspExecutor.submit(any<Callable<PaymentOrderStatus>>()) } answers { 
            val task = firstArg<Callable<PaymentOrderStatus>>()
            try {
                CompletableFuture.completedFuture(task.call())
            } catch (e: Exception) {
                CompletableFuture.failedFuture(e)
            }
        }

        adapter = PspCaptureGatewayAdapter(
            simulator = networkSimulator,
            config = config,
            pspExecutor = pspExecutor,
            meterRegistry = meterRegistry
        )
    }
}
