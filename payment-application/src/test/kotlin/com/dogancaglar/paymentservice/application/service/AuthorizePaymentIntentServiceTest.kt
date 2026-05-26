package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.command.AuthorizePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.outbound.*
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthorizePaymentIntentServiceTest {

    private lateinit var idGeneratorPort: IdGeneratorPort
    private lateinit var paymentIntentRepository: PaymentIntentRepository
    private lateinit var pspAuthGatewayPort: PspAuthorizationGatewayPort
    private lateinit var resilientExecutionPort: ResilientExecutionPort
    private lateinit var serializationPort: SerializationPort
    private lateinit var paymentTransactionalFacadePort: PaymentTransactionalFacadePort
    private lateinit var service: AuthorizePaymentIntentService

    @BeforeEach
    fun setUp() {
        idGeneratorPort = mockk()
        paymentIntentRepository = mockk()
        pspAuthGatewayPort = mockk()
        resilientExecutionPort = mockk()
        serializationPort = mockk()
        paymentTransactionalFacadePort = mockk()

        service = AuthorizePaymentIntentService(
            idGeneratorPort = idGeneratorPort,
            paymentIntentRepository = paymentIntentRepository,
            pspAuthGatewayPort = pspAuthGatewayPort,
            resilientExecutionPort = resilientExecutionPort,
            serializationPort = serializationPort,
            paymentTransactionalFacadePort = paymentTransactionalFacadePort
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun createPaymentIntent(status: PaymentIntentStatus): PaymentIntent {
        val now = Utc.nowLocalDateTime()
        return PaymentIntent.rehydrate(
            paymentIntentId = PaymentIntentId(100L),
            pspReference = if (status == PaymentIntentStatus.CREATED_PENDING) null else "psp_ref_123",
            buyerId = BuyerId("buyer-123"),
            orderId = OrderId("order-456"),
            totalAmount = Amount.of(1000, Currency("USD")),
            paymentOrderLines = listOf(
                PaymentOrderLine(SellerId("seller-1"), Amount.of(1000, Currency("USD")))
            ),
            status = status,
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun `should return immediately when payment intent is already authorized`() {
        // given
        val paymentIntentId = PaymentIntentId(100L)
        val cmd = AuthorizePaymentIntentCommand(paymentIntentId)
        val existingIntent = createPaymentIntent(PaymentIntentStatus.AUTHORIZED)

        every { paymentIntentRepository.findById(paymentIntentId) } returns existingIntent

        // when
        val result = service.authorize(cmd)

        // then
        assertEquals(PaymentIntentStatus.AUTHORIZED, result.status)
        assertEquals(paymentIntentId, result.paymentIntentId)
        verify(exactly = 0) { pspAuthGatewayPort.authorizePaymentIntent(any(), any()) }
        verify(exactly = 0) { paymentIntentRepository.tryMarkPendingAuth(any(), any()) }
    }
}