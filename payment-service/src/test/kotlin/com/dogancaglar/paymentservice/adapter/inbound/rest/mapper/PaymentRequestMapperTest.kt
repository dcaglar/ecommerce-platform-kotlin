package com.dogancaglar.paymentservice.adapter.inbound.rest.mapper

import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentLineDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentRequestDTO
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.port.out.web.dto.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PaymentRequestMapperTest {


    @Test
    fun `should map PaymentRequestDTO to CreatePaymentCommand correctly`() {
        // Given
        val dto = CreatePaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentLineDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(10000L, CurrencyEnum.USD)
                )
            )
        )

        // When
        val command = PaymentRequestMapper.toCreatePaymentCommand(dto)

        // Then
        assertEquals(OrderId("order-123"), command.orderId)
        assertEquals(BuyerId("buyer-456"), command.buyerId)
        assertEquals(Amount.of(10000L, Currency("USD")), command.totalAmount)
        assertEquals(1, command.paymentLines.size)
        assertEquals(SellerId("seller-789"), command.paymentLines[0].sellerId)
        assertEquals(Amount.of(10000L, Currency("USD")), command.paymentLines[0].amount)
    }

    @Test
    fun `should map PaymentRequestDTO with multiple payment orders correctly`() {
        // Given
        val dto = CreatePaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(20000L, CurrencyEnum.EUR),
            paymentOrders = listOf(
                PaymentLineDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(15000L, CurrencyEnum.EUR)
                ),
                PaymentLineDTO(
                    sellerId = "seller-101",
                    amount = AmountDto(5000L, CurrencyEnum.EUR)
                )
            )
        )

        // When
        val command = PaymentRequestMapper.toCreatePaymentCommand(dto)

        // Then
        assertEquals(OrderId("order-123"), command.orderId)
        assertEquals(BuyerId("buyer-456"), command.buyerId)
        assertEquals(Amount.of(20000L, Currency("EUR")), command.totalAmount)
        assertEquals(2, command.paymentLines.size)
        assertEquals(SellerId("seller-789"), command.paymentLines[0].sellerId)
        assertEquals(Amount.of(15000L, Currency("EUR")), command.paymentLines[0].amount)
        assertEquals(SellerId("seller-101"), command.paymentLines[1].sellerId)
        assertEquals(Amount.of(5000L, Currency("EUR")), command.paymentLines[1].amount)
    }

    @Test
    fun `should map Payment to PaymentResponseDTO correctly`() {
        val payment = Payment.createNew(
            paymentId = PaymentId(123L),
            buyerId = BuyerId("buyer-456"),
            orderId = OrderId("order-123"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(10000L, Currency("USD"))
                )
            )
        )

        val response = PaymentRequestMapper.toPaymentResponseDto(payment)

        assertEquals(payment.paymentId.toPublicPaymentId(), response.paymentId)
        assertEquals("CREATED", response.status)
        assertEquals("buyer-456", response.buyerId)
        assertEquals("order-123", response.orderId)
        assertEquals(10000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.USD, response.totalAmount.currency)
        assertEquals(payment.createdAt.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME), response.createdAt)
    }

    @Test
    fun `should map Payment with different status correctly`() {
        val payment = Payment.createNew(
            paymentId = PaymentId(456L),
            buyerId = BuyerId("buyer-789"),
            orderId = OrderId("order-456"),
            totalAmount = Amount.of(5000L, Currency("EUR")),
            paymentLines = listOf(
                PaymentLine(
                    sellerId = SellerId("seller-1"),
                    amount = Amount.of(5000L, Currency("EUR"))
                )
            )
        )
            .startAuthorization()
            .authorize()

        val response = PaymentRequestMapper.toPaymentResponseDto(payment)

        assertEquals(payment.paymentId.toPublicPaymentId(), response.paymentId)
        assertEquals("AUTHORIZED", response.status)
        assertEquals(5000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.EUR, response.totalAmount.currency)
    }

    @Test
    fun `should handle large amounts correctly`() {
        // Given
        val dto = CreatePaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(99999999L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentLineDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(99999999L, CurrencyEnum.USD)
                )
            )
        )

        // When
        val command = PaymentRequestMapper.toCreatePaymentCommand(dto)

        // Then
        assertEquals(Amount.of(99999999L, Currency("USD")), command.totalAmount)
        assertEquals(Amount.of(99999999L, Currency("USD")), command.paymentLines[0].amount)
    }

    @Test
    fun `should handle different currencies correctly`() {
        // Given
        val dto = CreatePaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.GBP),
            paymentOrders = listOf(
                PaymentLineDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(10000L, CurrencyEnum.GBP)
                )
            )
        )

        // When
        val command = PaymentRequestMapper.toCreatePaymentCommand(dto)

        // Then
        assertEquals(Amount.of(10000L, Currency("GBP")), command.totalAmount)
        assertEquals(Amount.of(10000L, Currency("GBP")), command.paymentLines[0].amount)
    }
}