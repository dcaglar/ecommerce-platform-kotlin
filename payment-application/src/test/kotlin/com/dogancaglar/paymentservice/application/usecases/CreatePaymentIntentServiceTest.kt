package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.exception.PspPermanentException
import com.dogancaglar.paymentservice.domain.exception.PspTransientException
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreatePaymentIntentServiceTest {

    private lateinit var paymentIntentRepository: PaymentIntentRepository
    private lateinit var idGeneratorPort: IdGeneratorPort
    private lateinit var pspAuthGatewayPort: PspAuthorizationGatewayPort
    private lateinit var pspCallbackExecutor: Executor
    private lateinit var service: CreatePaymentIntentService

    @BeforeEach
    fun setUp() {
        paymentIntentRepository = mockk()
        idGeneratorPort = mockk()
        pspAuthGatewayPort = mockk()
        pspCallbackExecutor = mockk(relaxed = true)
        
        service = CreatePaymentIntentService(
            paymentIntentRepository = paymentIntentRepository,
            idGeneratorPort = idGeneratorPort,
            pspAuthGatewayPort = pspAuthGatewayPort,
            pspCallbackExecutor = pspCallbackExecutor
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun createCommand(): CreatePaymentIntentCommand {
        return CreatePaymentIntentCommand(
            buyerId = BuyerId("buyer-123"),
            orderId = OrderId("order-456"),
            totalAmount = Amount.of(10_000, Currency("EUR")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(10_000, Currency("EUR"))
                )
            )
        )
    }

    @Test
    fun `create succeeds when future completes with PaymentIntent having pspReference within timeout`() {
        // given
        val command = createCommand()
        val paymentIntentId = PaymentIntentId(100L)
        val initialPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = command.buyerId,
            orderId = command.orderId,
            totalAmount = command.totalAmount,
            paymentOrderLines = command.paymentOrderLines
        )

        val pspReference = "pi_stripe123"
        val clientSecret = "secret_abc123"
        val completedPaymentIntent = initialPaymentIntent.markAsCreatedWithPspReferenceAndClientSecret(
            pspReference = pspReference,
            clientSecret = clientSecret
        )

        val future = CompletableFuture.completedFuture(completedPaymentIntent)

        every { idGeneratorPort.nextPaymentIntentId(command.buyerId, command.orderId) } returns 100L
        every { paymentIntentRepository.save(any()) } returns initialPaymentIntent
        every { pspAuthGatewayPort.createIntent(any(), any()) } returns future
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs

        // when
        val result = service.create(command)

        // then
        assertEquals(PaymentIntentStatus.CREATED, result.status)
        assertEquals(pspReference, result.pspReference)
        assertEquals(clientSecret, result.clientSecret)
        
        verify(exactly = 1) { paymentIntentRepository.save(any()) }
        verify(exactly = 1) { 
            paymentIntentRepository.updatePaymentIntent(match { 
                it.status == PaymentIntentStatus.CREATED && 
                it.pspReference == pspReference 
            }) 
        }
        verify(exactly = 1) { pspAuthGatewayPort.createIntent("create:100", any()) }
    }

    @Test
    fun `create returns CREATED_PENDING when future times out`() {
        // given
        val command = createCommand()
        val paymentIntentId = PaymentIntentId(200L)
        val initialPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = command.buyerId,
            orderId = command.orderId,
            totalAmount = command.totalAmount,
            paymentOrderLines = command.paymentOrderLines
        )

        // Future that will not complete (simulating timeout)
        val future = CompletableFuture<PaymentIntent>()

        every { idGeneratorPort.nextPaymentIntentId(command.buyerId, command.orderId) } returns 200L
        every { paymentIntentRepository.save(any()) } returns initialPaymentIntent
        every { pspAuthGatewayPort.createIntent(any(), any()) } returns future
        // whenCompleteAsync uses the executor, but we can't easily verify it without waiting
        // Instead, we verify the behavior: CREATED_PENDING is returned and updatePaymentIntent is NOT called

        // when
        val result = service.create(command)

        // then
        assertEquals(PaymentIntentStatus.CREATED_PENDING, result.status)
        assertNull(result.pspReference, "pspReference should be null for CREATED_PENDING")
        
        verify(exactly = 1) { paymentIntentRepository.save(any()) }
        verify(exactly = 0) { paymentIntentRepository.updatePaymentIntent(any()) } // Should NOT be called on timeout
        verify(exactly = 1) { pspAuthGatewayPort.createIntent("create:200", any()) }
        // Note: whenCompleteAsync schedules on executor, but we verify behavior instead of internal implementation
    }

    @Test
    fun `create handles PspPermanentException and marks PaymentIntent as CANCELLED`() {
        // given
        val command = createCommand()
        val paymentIntentId = PaymentIntentId(300L)
        val initialPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = command.buyerId,
            orderId = command.orderId,
            totalAmount = command.totalAmount,
            paymentOrderLines = command.paymentOrderLines
        )

        val permanentException = PspPermanentException("Stripe permanent failure", RuntimeException("Stripe error"))
        val future = CompletableFuture<PaymentIntent>()
        future.completeExceptionally(CompletionException(permanentException))

        every { idGeneratorPort.nextPaymentIntentId(command.buyerId, command.orderId) } returns 300L
        every { paymentIntentRepository.save(any()) } returns initialPaymentIntent
        every { pspAuthGatewayPort.createIntent(any(), any()) } returns future
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs

        // when
        val result = service.create(command)

        // then
        assertEquals(PaymentIntentStatus.CANCELLED, result.status)
        assertNull(result.pspReference, "pspReference should be null for CANCELLED")
        
        verify(exactly = 1) { paymentIntentRepository.save(any()) }
        verify(exactly = 1) { 
            paymentIntentRepository.updatePaymentIntent(match { 
                it.status == PaymentIntentStatus.CANCELLED 
            }) 
        }
        verify(exactly = 1) { pspAuthGatewayPort.createIntent("create:300", any()) }
    }

    @Test
    fun `create handles PspTransientException and returns CREATED_PENDING`() {
        // given
        val command = createCommand()
        val paymentIntentId = PaymentIntentId(400L)
        val initialPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = command.buyerId,
            orderId = command.orderId,
            totalAmount = command.totalAmount,
            paymentOrderLines = command.paymentOrderLines
        )

        val transientException = PspTransientException("Stripe transient failure", RuntimeException("Network error"))
        val future = CompletableFuture<PaymentIntent>()
        future.completeExceptionally(CompletionException(transientException))

        every { idGeneratorPort.nextPaymentIntentId(command.buyerId, command.orderId) } returns 400L
        every { paymentIntentRepository.save(any()) } returns initialPaymentIntent
        every { pspAuthGatewayPort.createIntent(any(), any()) } returns future

        // when
        val result = service.create(command)

        // then
        assertEquals(PaymentIntentStatus.CREATED_PENDING, result.status)
        assertNull(result.pspReference, "pspReference should be null for CREATED_PENDING")
        
        verify(exactly = 1) { paymentIntentRepository.save(any()) }
        verify(exactly = 0) { paymentIntentRepository.updatePaymentIntent(any()) } // Should NOT update for transient
        verify(exactly = 1) { pspAuthGatewayPort.createIntent("create:400", any()) }
        // Note: Scheduling retry is out of scope for now
    }


    @Test
    fun `background completion handler updates PaymentIntent when future completes successfully after timeout`() {
        // given
        val command = createCommand()
        val paymentIntentId = PaymentIntentId(600L)
        val initialPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = command.buyerId,
            orderId = command.orderId,
            totalAmount = command.totalAmount,
            paymentOrderLines = command.paymentOrderLines
        )

        val pspReference = "pi_background123"
        val clientSecret = "secret_background"
        val completedPaymentIntent = initialPaymentIntent.markAsCreatedWithPspReferenceAndClientSecret(
            pspReference = pspReference,
            clientSecret = clientSecret
        )

        // Future that completes after timeout
        val future = CompletableFuture<PaymentIntent>()

        // Use a real executor for this test so whenCompleteAsync actually runs
        val realExecutor = Executors.newFixedThreadPool(1)

        val serviceWithRealExecutor = CreatePaymentIntentService(
            paymentIntentRepository = paymentIntentRepository,
            idGeneratorPort = idGeneratorPort,
            pspAuthGatewayPort = pspAuthGatewayPort,
            pspCallbackExecutor = realExecutor
        )

        every { idGeneratorPort.nextPaymentIntentId(command.buyerId, command.orderId) } returns 600L
        every { paymentIntentRepository.save(any()) } returns initialPaymentIntent
        every { pspAuthGatewayPort.createIntent(any(), any()) } returns future
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs

        // when
        val result = serviceWithRealExecutor.create(command)

        // then - Should return CREATED_PENDING immediately (timeout case)
        assertEquals(PaymentIntentStatus.CREATED_PENDING, result.status)
        
        // Simulate future completing after timeout (this triggers whenCompleteAsync callback)
        future.complete(completedPaymentIntent)
        
        // Wait for the async callback to execute
        Thread.sleep(500)
        
        // Verify background completion updated the PaymentIntent
        verify(exactly = 1) { 
            paymentIntentRepository.updatePaymentIntent(match { 
                it.status == PaymentIntentStatus.CREATED && 
                it.pspReference == pspReference 
            }) 
        }
        
        realExecutor.shutdown()
    }

    @Test
    fun `background completion handler marks PaymentIntent as CANCELLED when PspPermanentException occurs`() {
        // given
        val command = createCommand()
        val paymentIntentId = PaymentIntentId(700L)
        val initialPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = command.buyerId,
            orderId = command.orderId,
            totalAmount = command.totalAmount,
            paymentOrderLines = command.paymentOrderLines
        )

        val permanentException = PspPermanentException("Background permanent failure", RuntimeException())
        val future = CompletableFuture<PaymentIntent>()

        every { idGeneratorPort.nextPaymentIntentId(command.buyerId, command.orderId) } returns 700L
        every { paymentIntentRepository.save(any()) } returns initialPaymentIntent
        every { pspAuthGatewayPort.createIntent(any(), any()) } returns future
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs
        
        var capturedRunnable: Runnable? = null
        every { pspCallbackExecutor.execute(any()) } answers {
            capturedRunnable = firstArg()
        }

        // when
        val result = service.create(command)

        // then - Should return CREATED_PENDING immediately (timeout case)
        assertEquals(PaymentIntentStatus.CREATED_PENDING, result.status)
        
        // Simulate future completing with exception after timeout
        future.completeExceptionally(CompletionException(permanentException))
        
        // Execute the captured completion handler
        capturedRunnable?.run()
        
        // Verify background completion marked as CANCELLED
        verify(exactly = 1) { 
            paymentIntentRepository.updatePaymentIntent(match { 
                it.status == PaymentIntentStatus.CANCELLED 
            }) 
        }
    }

    @Test
    fun `background completion handler handles PspTransientException without updating PaymentIntent`() {
        // given
        val command = createCommand()
        val paymentIntentId = PaymentIntentId(800L)
        val initialPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = command.buyerId,
            orderId = command.orderId,
            totalAmount = command.totalAmount,
            paymentOrderLines = command.paymentOrderLines
        )

        val transientException = PspTransientException("Background transient failure", RuntimeException())
        val future = CompletableFuture<PaymentIntent>()

        every { idGeneratorPort.nextPaymentIntentId(command.buyerId, command.orderId) } returns 800L
        every { paymentIntentRepository.save(any()) } returns initialPaymentIntent
        every { pspAuthGatewayPort.createIntent(any(), any()) } returns future
        
        var capturedRunnable: Runnable? = null
        every { pspCallbackExecutor.execute(any()) } answers {
            capturedRunnable = firstArg()
        }

        // when
        val result = service.create(command)

        // then - Should return CREATED_PENDING immediately (timeout case)
        assertEquals(PaymentIntentStatus.CREATED_PENDING, result.status)
        
        // Simulate future completing with transient exception after timeout
        future.completeExceptionally(CompletionException(transientException))
        
        // Execute the captured completion handler
        capturedRunnable?.run()
        
        // Verify background completion does NOT update (transient exception handling is out of scope)
        verify(exactly = 0) { 
            paymentIntentRepository.updatePaymentIntent(any()) 
        }
    }

    @Test
    fun `create saves PaymentIntent with correct initial state`() {
        // given
        val command = createCommand()
        val paymentIntentId = PaymentIntentId(900L)
        
        every { idGeneratorPort.nextPaymentIntentId(command.buyerId, command.orderId) } returns 900L
        every { paymentIntentRepository.save(any()) } answers { firstArg<PaymentIntent>() }
        every { pspAuthGatewayPort.createIntent(any(), any()) } returns CompletableFuture.completedFuture(
            PaymentIntent.createNew(
                paymentIntentId = paymentIntentId,
                buyerId = command.buyerId,
                orderId = command.orderId,
                totalAmount = command.totalAmount,
                paymentOrderLines = command.paymentOrderLines
            ).markAsCreatedWithPspReferenceAndClientSecret("pi_test", "secret_test")
        )
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs

        // when
        service.create(command)

        // then - Verify initial save has correct state
        verify(exactly = 1) { 
            paymentIntentRepository.save(match { 
                it.paymentIntentId.value == 900L &&
                it.status == PaymentIntentStatus.CREATED_PENDING &&
                it.pspReference == null &&
                it.buyerId == command.buyerId &&
                it.orderId == command.orderId
            }) 
        }
    }

    @Test
    fun `create uses correct idempotency key format for Stripe`() {
        // given
        val command = createCommand()
        val paymentIntentId = PaymentIntentId(1000L)
        val initialPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = command.buyerId,
            orderId = command.orderId,
            totalAmount = command.totalAmount,
            paymentOrderLines = command.paymentOrderLines
        )

        every { idGeneratorPort.nextPaymentIntentId(command.buyerId, command.orderId) } returns 1000L
        every { paymentIntentRepository.save(any()) } returns initialPaymentIntent
        every { pspAuthGatewayPort.createIntent(any(), any()) } returns CompletableFuture.completedFuture(
            initialPaymentIntent.markAsCreatedWithPspReferenceAndClientSecret("pi_1000", "secret_1000")
        )
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs

        // when
        service.create(command)

        // then - Verify idempotency key format
        verify(exactly = 1) { 
            pspAuthGatewayPort.createIntent("create:1000", any()) 
        }
    }
}

