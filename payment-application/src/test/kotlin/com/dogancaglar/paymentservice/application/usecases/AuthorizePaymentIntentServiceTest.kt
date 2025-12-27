package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.commands.AuthorizePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.exception.PaymentNotReadyException
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
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthorizePaymentIntentServiceTest {

    private lateinit var idGeneratorPort: IdGeneratorPort
    private lateinit var paymentIntentRepository: PaymentIntentRepository
    private lateinit var psp: PspAuthorizationGatewayPort
    private lateinit var serializationPort: SerializationPort
    private lateinit var paymentTransactionalFacadePort: PaymentTransactionalFacadePort
    private lateinit var paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
    private lateinit var service: AuthorizePaymentIntentService

    @BeforeEach
    fun setUp() {
        idGeneratorPort = mockk()
        paymentIntentRepository = mockk()
        psp = mockk()
        serializationPort = mockk()
        paymentTransactionalFacadePort = mockk()
        paymentOrderDomainEventMapper = PaymentOrderDomainEventMapper()

        service = AuthorizePaymentIntentService(
            idGeneratorPort = idGeneratorPort,
            paymentIntentRepository = paymentIntentRepository,
            psp = psp,
            serializationPort = serializationPort,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper,
            paymentTransactionalFacadePort = paymentTransactionalFacadePort
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun createPaymentIntentWithPspReference(): PaymentIntent {
        return PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(100L),
            buyerId = BuyerId("buyer-123"),
            orderId = OrderId("order-456"),
            totalAmount = Amount.of(10_000, Currency("EUR")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(10_000, Currency("EUR"))
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret(
            pspReference = "pi_stripe123",
            clientSecret = "secret_abc123"
        )
    }

    private fun createCommand(paymentIntentId: PaymentIntentId = PaymentIntentId(100L)): AuthorizePaymentIntentCommand {
        return AuthorizePaymentIntentCommand(
            paymentIntentId = paymentIntentId,
            paymentMethod = null
        )
    }

    @Test
    fun `authorize succeeds when Stripe confirms with AUTHORIZED status within timeout`() {
        // given
        val command = createCommand()
        val paymentIntent = createPaymentIntentWithPspReference()
        val pendingPaymentIntent = paymentIntent.markAuthorizedPending()

        every { paymentIntentRepository.findById(command.paymentIntentId) } returns paymentIntent
        every { paymentIntentRepository.tryMarkPendingAuth(command.paymentIntentId, any()) } returns true
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs
        every { psp.authorize("confirm:100", any(), null) } returns PaymentIntentStatus.AUTHORIZED
        every { idGeneratorPort.nextPaymentId(any(), any()) } returns 200L
        every { idGeneratorPort.nextPaymentOrderId(any()) } returns 300L
        every { serializationPort.toJson(any<Any>()) } returns "{}"
        every { paymentTransactionalFacadePort.handleAuthorized(any(), any(), any(), any()) } just Runs

        // when
        val result = service.authorize(command)

        // then
        assertEquals(PaymentIntentStatus.AUTHORIZED, result.status)
        assertNotNull(result.pspReference)

        verify(exactly = 1) { paymentIntentRepository.findById(command.paymentIntentId) }
        verify(exactly = 1) { paymentIntentRepository.tryMarkPendingAuth(command.paymentIntentId, any()) }
        verify(exactly = 0) { paymentIntentRepository.updatePaymentIntent(any()) }
        verify(exactly = 1) { psp.authorize("confirm:100", match<PaymentIntent> { it.status == PaymentIntentStatus.PENDING_AUTH }, null) }
        verify(exactly = 1) { 
            paymentTransactionalFacadePort.handleAuthorized(
                match<PaymentIntent> { it.status == PaymentIntentStatus.AUTHORIZED },
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `authorize returns PENDING_AUTH when PSP returns PENDING_AUTH status`() {
        // given
        val command = createCommand()
        val paymentIntent = createPaymentIntentWithPspReference()
        val pendingPaymentIntent = paymentIntent.markAuthorizedPending()

        every { paymentIntentRepository.findById(command.paymentIntentId) } returns paymentIntent
        every { paymentIntentRepository.tryMarkPendingAuth(command.paymentIntentId, any()) } returns true
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs
        every { psp.authorize("confirm:100", any(), null) } returns PaymentIntentStatus.PENDING_AUTH

        // when
        val result = service.authorize(command)

        // then
        assertEquals(PaymentIntentStatus.PENDING_AUTH, result.status)
        
        verify(exactly = 0) { paymentIntentRepository.updatePaymentIntent(any()) }
        verify(exactly = 1) { psp.authorize("confirm:100", match<PaymentIntent> { it.status == PaymentIntentStatus.PENDING_AUTH }, null) }
        verify(exactly = 0) { paymentTransactionalFacadePort.handleAuthorized(any(), any(), any(), any()) }
    }

    @Test
    fun `authorize handles DECLINED status from PSP and marks PaymentIntent as DECLINED`() {
        // given
        val command = createCommand()
        val paymentIntent = createPaymentIntentWithPspReference()
        val pendingPaymentIntent = paymentIntent.markAuthorizedPending()

        every { paymentIntentRepository.findById(command.paymentIntentId) } returns paymentIntent
        every { paymentIntentRepository.tryMarkPendingAuth(command.paymentIntentId, any()) } returns true
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs
        every { psp.authorize("confirm:100", any(), null) } returns PaymentIntentStatus.DECLINED

        // when
        val result = service.authorize(command)

        // then
        assertEquals(PaymentIntentStatus.DECLINED, result.status)
        
        verify(exactly = 0) { paymentIntentRepository.updatePaymentIntent(any()) }
        verify(exactly = 1) { psp.authorize("confirm:100", match<PaymentIntent> { it.status == PaymentIntentStatus.PENDING_AUTH }, null) }
        verify(exactly = 0) { paymentTransactionalFacadePort.handleAuthorized(any(), any(), any(), any()) }
    }

    @Test
    fun `authorize handles PENDING_AUTH status from PSP and returns PENDING_AUTH`() {
        // given
        val command = createCommand()
        val paymentIntent = createPaymentIntentWithPspReference()
        val pendingPaymentIntent = paymentIntent.markAuthorizedPending()

        every { paymentIntentRepository.findById(command.paymentIntentId) } returns paymentIntent
        every { paymentIntentRepository.tryMarkPendingAuth(command.paymentIntentId, any()) } returns true
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs
        every { psp.authorize("confirm:100", any(), null) } returns PaymentIntentStatus.PENDING_AUTH

        // when
        val result = service.authorize(command)

        // then
        assertEquals(PaymentIntentStatus.PENDING_AUTH, result.status)
        
        verify(exactly = 0) { paymentIntentRepository.updatePaymentIntent(any()) }
        verify(exactly = 1) { psp.authorize("confirm:100", match<PaymentIntent> { it.status == PaymentIntentStatus.PENDING_AUTH }, null) }
        verify(exactly = 0) { paymentTransactionalFacadePort.handleAuthorized(any(), any(), any(), any()) }
    }

    @Test
    fun `authorize throws PaymentNotReadyException when PaymentIntent status is CREATED_PENDING`() {
        // given
        val command = createCommand()
        val paymentIntent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(100L),
            buyerId = BuyerId("buyer-123"),
            orderId = OrderId("order-456"),
            totalAmount = Amount.of(10_000, Currency("EUR")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(10_000, Currency("EUR"))
                )
            )
        ) // Status is CREATED_PENDING

        every { paymentIntentRepository.findById(command.paymentIntentId) } returns paymentIntent

        // when/then
        assertThrows<PaymentNotReadyException> {
            service.authorize(command)
        }

        verify(exactly = 1) { paymentIntentRepository.findById(command.paymentIntentId) }
        verify(exactly = 0) { paymentIntentRepository.tryMarkPendingAuth(any(), any()) }
        verify(exactly = 0) { psp.authorize(any(), any(), any()) }
    }

    @Test
    fun `authorize returns PaymentIntent when status is already AUTHORIZED (idempotent)`() {
        // given
        val command = createCommand()
        val paymentIntent = createPaymentIntentWithPspReference()
            .markAuthorizedPending()
            .markAuthorized()

        every { paymentIntentRepository.findById(command.paymentIntentId) } returns paymentIntent

        // when
        val result = service.authorize(command)

        // then
        assertEquals(PaymentIntentStatus.AUTHORIZED, result.status)
        
        verify(exactly = 1) { paymentIntentRepository.findById(command.paymentIntentId) }
        verify(exactly = 0) { paymentIntentRepository.tryMarkPendingAuth(any(), any()) }
        verify(exactly = 0) { psp.authorize(any(), any(), any()) }
    }

    @Test
    fun `authorize returns PaymentIntent when status is already DECLINED (idempotent)`() {
        // given
        val command = createCommand()
        val paymentIntent = createPaymentIntentWithPspReference()
            .markAuthorizedPending()
            .markDeclined()

        every { paymentIntentRepository.findById(command.paymentIntentId) } returns paymentIntent

        // when
        val result = service.authorize(command)

        // then
        assertEquals(PaymentIntentStatus.DECLINED, result.status)
        
        verify(exactly = 1) { paymentIntentRepository.findById(command.paymentIntentId) }
        verify(exactly = 0) { paymentIntentRepository.tryMarkPendingAuth(any(), any()) }
        verify(exactly = 0) { psp.authorize(any(), any(), any()) }
    }

    @Test
    fun `authorize returns PaymentIntent when status is already PENDING_AUTH (idempotent)`() {
        // given
        val command = createCommand()
        val paymentIntent = createPaymentIntentWithPspReference()
            .markAuthorizedPending()

        every { paymentIntentRepository.findById(command.paymentIntentId) } returns paymentIntent

        // when
        val result = service.authorize(command)

        // then
        assertEquals(PaymentIntentStatus.PENDING_AUTH, result.status)
        
        verify(exactly = 1) { paymentIntentRepository.findById(command.paymentIntentId) }
        verify(exactly = 0) { paymentIntentRepository.tryMarkPendingAuth(any(), any()) }
        verify(exactly = 0) { psp.authorize(any(), any(), any()) }
    }

    @Test
    fun `authorize returns latest state when tryMarkPendingAuth returns false (concurrent request)`() {
        // given
        val command = createCommand()
        val paymentIntent = createPaymentIntentWithPspReference()
        val concurrentPaymentIntent = paymentIntent.markAuthorizedPending().markAuthorized()

        every { paymentIntentRepository.findById(command.paymentIntentId) } returns paymentIntent andThen concurrentPaymentIntent
        every { paymentIntentRepository.tryMarkPendingAuth(command.paymentIntentId, any()) } returns false

        // when
        val result = service.authorize(command)

        // then
        assertEquals(PaymentIntentStatus.AUTHORIZED, result.status)
        
        verify(exactly = 2) { paymentIntentRepository.findById(command.paymentIntentId) }
        verify(exactly = 1) { paymentIntentRepository.tryMarkPendingAuth(command.paymentIntentId, any()) }
        verify(exactly = 0) { psp.confirmIntent(any(), any(), any()) }
    }

    @Test
    fun `authorize uses correct idempotency key format for Stripe confirmIntent`() {
        // given
        val command = createCommand(PaymentIntentId(500L))
        val paymentIntent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(500L),
            buyerId = BuyerId("buyer-123"),
            orderId = OrderId("order-456"),
            totalAmount = Amount.of(10_000, Currency("EUR")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(10_000, Currency("EUR"))
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret(
            pspReference = "pi_test500",
            clientSecret = "secret_test500"
        )

        every { paymentIntentRepository.findById(command.paymentIntentId) } returns paymentIntent
        every { paymentIntentRepository.tryMarkPendingAuth(command.paymentIntentId, any()) } returns true
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs
        every { psp.authorize(any(), any(), any()) } returns PaymentIntentStatus.AUTHORIZED
        every { idGeneratorPort.nextPaymentId(any(), any()) } returns 600L
        every { idGeneratorPort.nextPaymentOrderId(any()) } returns 700L
        every { serializationPort.toJson(any<Any>()) } returns "{}"
        every { paymentTransactionalFacadePort.handleAuthorized(any(), any(), any(), any()) } just Runs

        // when
        service.authorize(command)

        // then - Verify idempotency key format
        verify(exactly = 1) { 
            psp.authorize("confirm:500", match<PaymentIntent> { it.paymentIntentId.value == 500L }, null) 
        }
    }


}








