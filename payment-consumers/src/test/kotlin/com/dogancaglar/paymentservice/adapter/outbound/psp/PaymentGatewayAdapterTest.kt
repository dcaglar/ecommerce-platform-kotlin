package com.dogancaglar.paymentservice.adapter.outbound.psp

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.*

class PaymentGatewayAdapterTest {

    private lateinit var networkSimulator: NetworkSimulator
    private lateinit var config: PspSimulationProperties
    private lateinit var pspExecutor: ThreadPoolTaskExecutor
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var clock: Clock
    private lateinit var adapter: PaymentGatewayAdapter

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
        
        // Mock NetworkSimulator behavior
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

        adapter = PaymentGatewayAdapter(
            simulator = networkSimulator,
            config = config,
            pspExecutor = pspExecutor,
            meterRegistry = meterRegistry
        )
    }

    @Test
    fun `should process successful payment charge`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig(
            successful = 100,
            retryable = 0,
            nonRetryable = 0
        )

        every { config.scenario } returns PspScenario.NORMAL
        every { config.scenarios } returns mapOf(PspScenario.NORMAL to scenarioConfig)
        every { networkSimulator.simulate() } just Runs
        every { networkSimulator.simulate() } returns Unit

        // When
        val result = adapter.charge(paymentOrder)

        // Then
        assertEquals(PaymentOrderStatus.SUCCESSFUL_FINAL, result)
        verify { networkSimulator.simulate() }
        verify { pspExecutor.submit(any<Callable<PaymentOrderStatus>>()) }
    }

    @Test
    fun `should handle failed payment charge`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig(
            successful = 0,
            retryable = 100,
            nonRetryable = 0
        )

        every { config.scenario } returns PspScenario.PEAK
        every { config.scenarios } returns mapOf(PspScenario.PEAK to scenarioConfig)
        every { networkSimulator.simulate() } just Runs
        every { networkSimulator.simulate() } returns Unit

        // When
        val result = adapter.charge(paymentOrder)

        // Then
        assertEquals(PaymentOrderStatus.FAILED_TRANSIENT_ERROR, result)
        verify { networkSimulator.simulate() }
    }

    @Test
    fun `should handle declined payment charge`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig(
            successful = 0,
            retryable = 0,
            nonRetryable = 100
        )

        every { config.scenario } returns PspScenario.DEGRADED
        every { config.scenarios } returns mapOf(PspScenario.DEGRADED to scenarioConfig)
        every { networkSimulator.simulate() } just Runs
        every { networkSimulator.simulate() } returns Unit

        // When
        val result = adapter.charge(paymentOrder)

        // Then
        assertEquals(PaymentOrderStatus.DECLINED_FINAL, result)
        verify { networkSimulator.simulate() }
    }

    @Test
    fun `should handle pending payment charge`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig(
            successful = 0,
            retryable = 0,
            nonRetryable = 0,
            statusCheck = 100
        )

        every { config.scenario } returns PspScenario.BEST_PSP_EVER
        every { config.scenarios } returns mapOf(PspScenario.BEST_PSP_EVER to scenarioConfig)
        every { networkSimulator.simulate() } just Runs
        every { networkSimulator.simulate() } returns Unit

        // When
        val result = adapter.charge(paymentOrder)

        // Then
        assertEquals(PaymentOrderStatus.UNKNOWN_FINAL, result)
        verify { networkSimulator.simulate() }
    }

    @Test
    fun `should handle timeout exception`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig()
        val future = mockk<Future<PaymentOrderStatus>>()

        every { config.scenario } returns PspScenario.NORMAL
        every { config.scenarios } returns mapOf(PspScenario.NORMAL to scenarioConfig)
        every { networkSimulator.simulate() } returns Unit
        every { pspExecutor.submit(any<Callable<PaymentOrderStatus>>()) } returns future
        every { future.get(500, TimeUnit.MILLISECONDS) } throws TimeoutException("Timeout")
        every { future.cancel(true) } returns true

        // When
        val result = adapter.charge(paymentOrder)

        // Then
        assertEquals(PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT, result)
        verify { future.cancel(true) }
    }

    @Test
    fun `should handle interrupted exception`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig()
        val future = mockk<Future<PaymentOrderStatus>>()

        every { config.scenario } returns PspScenario.NORMAL
        every { config.scenarios } returns mapOf(PspScenario.NORMAL to scenarioConfig)
        every { networkSimulator.simulate() } returns Unit
        every { pspExecutor.submit(any<Callable<PaymentOrderStatus>>()) } returns future
        every { future.get(500, TimeUnit.MILLISECONDS) } throws InterruptedException("Interrupted")
        every { future.cancel(true) } returns true

        // When
        val result = adapter.charge(paymentOrder)

        // Then
        assertEquals(PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT, result)
        verify { future.cancel(true) }
    }

    @Test
    fun `should handle cancellation exception`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig()
        val future = mockk<Future<PaymentOrderStatus>>()

        every { config.scenario } returns PspScenario.NORMAL
        every { config.scenarios } returns mapOf(PspScenario.NORMAL to scenarioConfig)
        every { networkSimulator.simulate() } returns Unit
        every { pspExecutor.submit(any<Callable<PaymentOrderStatus>>()) } returns future
        every { future.get(500, TimeUnit.MILLISECONDS) } throws CancellationException("Cancelled")

        // When
        val result = adapter.charge(paymentOrder)

        // Then
        assertEquals(PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT, result)
    }

    @Test
    fun `should handle execution exception with interrupted cause`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig()
        val future = mockk<Future<PaymentOrderStatus>>()
        val interruptedException = InterruptedException("Worker interrupted")

        every { config.scenario } returns PspScenario.NORMAL
        every { config.scenarios } returns mapOf(PspScenario.NORMAL to scenarioConfig)
        every { networkSimulator.simulate() } returns Unit
        every { pspExecutor.submit(any<Callable<PaymentOrderStatus>>()) } returns future
        every { future.get(500, TimeUnit.MILLISECONDS) } throws ExecutionException("Execution failed", interruptedException)

        // When
        val result = adapter.charge(paymentOrder)

        // Then
        assertEquals(PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT, result)
    }

    @Test
    fun `should handle execution exception with other cause`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig()
        val future = mockk<Future<PaymentOrderStatus>>()
        val runtimeException = RuntimeException("PSP error")

        every { config.scenario } returns PspScenario.NORMAL
        every { config.scenarios } returns mapOf(PspScenario.NORMAL to scenarioConfig)
        every { networkSimulator.simulate() } returns Unit
        every { pspExecutor.submit(any<Callable<PaymentOrderStatus>>()) } returns future
        every { future.get(500, TimeUnit.MILLISECONDS) } throws ExecutionException("Execution failed", runtimeException)

        // When & Then
        assertThrows<ExecutionException> {
            adapter.charge(paymentOrder)
        }
    }

    @Test
    fun `should handle rejected execution exception`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig()

        every { config.scenario } returns PspScenario.NORMAL
        every { config.scenarios } returns mapOf(PspScenario.NORMAL to scenarioConfig)
        every { pspExecutor.submit(any<Callable<PaymentOrderStatus>>()) } throws RejectedExecutionException("Pool saturated")

        // When
        val result = adapter.charge(paymentOrder)

        // Then
        assertEquals(PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT, result)
    }

    @Test
    fun `should process charge retry successfully`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()

        val scenarioConfig = createScenarioConfig(
            successful = 100,
            retryable = 0,
            nonRetryable = 0
        )

        every { config.scenario } returns PspScenario.NORMAL
        every { config.scenarios } returns mapOf(PspScenario.NORMAL to scenarioConfig)
        every { networkSimulator.simulate() } just Runs

        // When
        val result = adapter.chargeRetry(paymentOrder)

        // Then
        assertEquals(PaymentOrderStatus.SUCCESSFUL_FINAL, result)
        verify { networkSimulator.simulate() }
    }

    @Test
    fun `should check payment status successfully`() {
        // Given
        val paymentOrderId = "123"
        val scenarioConfig = createScenarioConfig(
            successful = 50,
            retryable = 0,
            nonRetryable = 50
        )

        every { config.scenario } returns PspScenario.NORMAL
        every { config.scenarios } returns mapOf(PspScenario.NORMAL to scenarioConfig)
        every { networkSimulator.simulate() } just Runs

        // When
        val result = adapter.checkPaymentStatus(paymentOrderId)

        // Then
        assertTrue(
            result == PaymentOrderStatus.CAPTURE_PENDING_STATUS_CHECK_LATER ||
            result == PaymentOrderStatus.SUCCESSFUL_FINAL ||
            result == PaymentOrderStatus.DECLINED_FINAL
        )
        verify { networkSimulator.simulate() }
    }

    private fun createScenarioConfig(
        successful: Int = 80,
        retryable: Int = 17,
        statusCheck: Int = 0,
        nonRetryable: Int = 3
    ): PspSimulationProperties.ScenarioConfig {
        val scenarioConfig = mockk<PspSimulationProperties.ScenarioConfig>()
        val responseDistribution = mockk<PspSimulationProperties.ResponseDistribution>()

        every { scenarioConfig.response } returns responseDistribution
        every { responseDistribution.successful } returns successful
        every { responseDistribution.retryable } returns retryable
        every { responseDistribution.statusCheck } returns statusCheck
        every { responseDistribution.nonRetryable } returns nonRetryable

        return scenarioConfig
    }
}
