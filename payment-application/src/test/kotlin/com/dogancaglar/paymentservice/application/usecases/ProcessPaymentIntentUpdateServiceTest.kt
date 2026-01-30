package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.commands.ProcessPaymentIntentUpdateCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProcessPaymentIntentUpdateServiceTest {

    private lateinit var paymentIntentRepository: PaymentIntentRepository
    private lateinit var service: UpdatePaymentIntentService

    @BeforeEach
    fun setUp() {
        paymentIntentRepository = mockk()
        service = UpdatePaymentIntentService(paymentIntentRepository)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun createPaymentIntent(status: PaymentIntentStatus = PaymentIntentStatus.CREATED_PENDING): PaymentIntent {
        val pi = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(100L),
            buyerId = BuyerId("buyer-123"),
            orderId = OrderId("order-456"),
            totalAmount = Amount.of(1000, Currency("USD")),
            paymentOrderLines = listOf(
                PaymentOrderLine(SellerId("seller-1"), Amount.of(1000, Currency("USD")))
            )
        )
        return when (status) {
            PaymentIntentStatus.CREATED_PENDING -> pi
            PaymentIntentStatus.CREATED -> pi.markAsCreatedWithPspReferenceAndClientSecret("pi_123", "secret_123")
            PaymentIntentStatus.AUTHORIZED -> pi.markAsCreatedWithPspReferenceAndClientSecret("pi_123", "secret_123")
                .markAuthorizedPending()
                .markAuthorized()
            else -> pi
        }
    }

    @Test
    fun `processUpdate throws exception when payment intent not found`() {
        // given
        val paymentIntentId = PaymentIntentId(100L)
        val cmd = ProcessPaymentIntentUpdateCommand(
            paymentIntentId = paymentIntentId,
            pspReference = "pi_123",
            clientSecret = "secret_123",
            status = PaymentIntentStatus.CREATED
        )
        // Since findById is non-nullable, it's expected to throw or return something if it was nullable.
        // The adapter implementation uses !! so it throws NPE if not found.
        every { paymentIntentRepository.findById(paymentIntentId) } throws NullPointerException()

        // when & then
        assertThrows<NullPointerException> {
            service.processUpdate(cmd)
        }
    }

    @Test
    fun `processUpdate transitions CREATED_PENDING to CREATED successfully`() {
        // given
        val paymentIntent = createPaymentIntent(PaymentIntentStatus.CREATED_PENDING)
        val cmd = ProcessPaymentIntentUpdateCommand(
            paymentIntentId = paymentIntent.paymentIntentId,
            pspReference = "pi_123",
            clientSecret = "secret_123",
            status = PaymentIntentStatus.CREATED
        )
        every { paymentIntentRepository.findById(paymentIntent.paymentIntentId) } returns paymentIntent
        every { paymentIntentRepository.updatePaymentIntent(any()) } just Runs

        // when
        val result = service.processUpdate(cmd)

        // then
        assertEquals(PaymentIntentStatus.CREATED, result.status)
        assertEquals("pi_123", result.pspReference)
        assertEquals("secret_123", result.clientSecret)
        verify(exactly = 1) { paymentIntentRepository.updatePaymentIntent(match { it.status == PaymentIntentStatus.CREATED }) }
    }

    @Test
    fun `processUpdate ignores non-CREATED transition for CREATED_PENDING`() {
        // given
        val paymentIntent = createPaymentIntent(PaymentIntentStatus.CREATED_PENDING)
        val cmd = ProcessPaymentIntentUpdateCommand(
            paymentIntentId = paymentIntent.paymentIntentId,
            pspReference = "pi_123",
            clientSecret = "secret_123",
            status = PaymentIntentStatus.AUTHORIZED // Invalid direct transition in this use case
        )
        every { paymentIntentRepository.findById(paymentIntent.paymentIntentId) } returns paymentIntent

        // when
        val result = service.processUpdate(cmd)

        // then
        assertEquals(PaymentIntentStatus.CREATED_PENDING, result.status)
        verify(exactly = 0) { paymentIntentRepository.updatePaymentIntent(any()) }
    }

    @Test
    fun `processUpdate returns existing when status is already CREATED (idempotency)`() {
        // given
        val paymentIntent = createPaymentIntent(PaymentIntentStatus.CREATED)
        val cmd = ProcessPaymentIntentUpdateCommand(
            paymentIntentId = paymentIntent.paymentIntentId,
            pspReference = "pi_123",
            clientSecret = "secret_123",
            status = PaymentIntentStatus.CREATED
        )
        every { paymentIntentRepository.findById(paymentIntent.paymentIntentId) } returns paymentIntent

        // when
        val result = service.processUpdate(cmd)

        // then
        assertEquals(PaymentIntentStatus.CREATED, result.status)
        verify(exactly = 0) { paymentIntentRepository.updatePaymentIntent(any()) }
    }

    @Test
    fun `processUpdate ignores updates when status is terminal or advanced`() {
        // given
        val paymentIntent = createPaymentIntent(PaymentIntentStatus.AUTHORIZED)
        val cmd = ProcessPaymentIntentUpdateCommand(
            paymentIntentId = paymentIntent.paymentIntentId,
            pspReference = "pi_123",
            clientSecret = "secret_123",
            status = PaymentIntentStatus.CREATED
        )
        every { paymentIntentRepository.findById(paymentIntent.paymentIntentId) } returns paymentIntent

        // when
        val result = service.processUpdate(cmd)

        // then
        assertEquals(PaymentIntentStatus.AUTHORIZED, result.status)
        verify(exactly = 0) { paymentIntentRepository.updatePaymentIntent(any()) }
    }
}
