package com.dogancaglar.paymentservice.adapter.inbound.rest.mapper

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.port.out.web.dto.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PaymentRequestMapperTest {

    private val clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `should map PaymentRequestDTO to CreatePaymentCommand correctly`() {
        // Given
        val dto = PaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentOrderRequestDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(10000L, CurrencyEnum.USD)
                )
            )
        )

        // When
        val command = PaymentRequestMapper.toCommand(dto)

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
        val dto = PaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(20000L, CurrencyEnum.EUR),
            paymentOrders = listOf(
                PaymentOrderRequestDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(15000L, CurrencyEnum.EUR)
                ),
                PaymentOrderRequestDTO(
                    sellerId = "seller-101",
                    amount = AmountDto(5000L, CurrencyEnum.EUR)
                )
            )
        )

        // When
        val command = PaymentRequestMapper.toCommand(dto)

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
        // Given
        val payment = Payment.builder()
            .paymentId(PaymentId(123L))
            .publicPaymentId("payment-123")
            .orderId(OrderId("order-123"))
            .buyerId(BuyerId("buyer-456"))
            .totalAmount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .paymentOrders(listOf())
            .buildNew()

        // When
        val response = PaymentRequestMapper.toResponse(payment)

        // Then
        assertEquals("payment-123", response.paymentId)
        assertEquals("payment-123", response.id)
        assertEquals("INITIATED", response.status)
        assertEquals("buyer-456", response.buyerId)
        assertEquals("order-123", response.orderId)
        assertEquals(10000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.USD, response.totalAmount.currency)
        assertNotNull(response.createdAt)
    }

    @Test
    fun `should map Payment with different status correctly`() {
        // Given
        val payment = Payment.builder()
            .paymentId(PaymentId(456L))
            .publicPaymentId("payment-456")
            .orderId(OrderId("order-456"))
            .buyerId(BuyerId("buyer-789"))
            .totalAmount(Amount.of(5000L, Currency("EUR")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .paymentOrders(listOf())
            .buildNew()

        // When
        val response = PaymentRequestMapper.toResponse(payment)

        // Then
        assertEquals("payment-456", response.paymentId)
        assertEquals("INITIATED", response.status)
        assertEquals(5000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.EUR, response.totalAmount.currency)
    }

    @Test
    fun `should handle large amounts correctly`() {
        // Given
        val dto = PaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(99999999L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentOrderRequestDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(99999999L, CurrencyEnum.USD)
                )
            )
        )

        // When
        val command = PaymentRequestMapper.toCommand(dto)

        // Then
        assertEquals(Amount.of(99999999L, Currency("USD")), command.totalAmount)
        assertEquals(Amount.of(99999999L, Currency("USD")), command.paymentLines[0].amount)
    }

    @Test
    fun `should handle different currencies correctly`() {
        // Given
        val dto = PaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.GBP),
            paymentOrders = listOf(
                PaymentOrderRequestDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(10000L, CurrencyEnum.GBP)
                )
            )
        )

        // When
        val command = PaymentRequestMapper.toCommand(dto)

        // Then
        assertEquals(Amount.of(10000L, Currency("GBP")), command.totalAmount)
        assertEquals(Amount.of(10000L, Currency("GBP")), command.paymentLines[0].amount)
    }
}