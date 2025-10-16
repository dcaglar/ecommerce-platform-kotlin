package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.application.constants.IdNamespaces
import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.vo.*
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import paymentservice.port.outbound.IdGeneratorPort
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class CreatePaymentServiceTest {

    private lateinit var idGeneratorPort: IdGeneratorPort
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var paymentOrderRepository: PaymentOrderRepository
    private lateinit var outboxEventPort: OutboxEventPort
    private lateinit var serializationPort: SerializationPort
    private lateinit var clock: Clock
    private lateinit var service: CreatePaymentService

    @BeforeEach
    fun setUp() {
        idGeneratorPort = mock<IdGeneratorPort>()
        paymentRepository = mock<PaymentRepository>()
        paymentOrderRepository = mock<PaymentOrderRepository>()
        outboxEventPort = mock<OutboxEventPort>()
        serializationPort = mock<SerializationPort>()
        clock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneId.of("UTC"))

        service = CreatePaymentService(
            idGeneratorPort = idGeneratorPort,
            paymentRepository = paymentRepository,
            paymentOrderRepository = paymentOrderRepository,
            outboxEventPort = outboxEventPort,
            serializationPort = serializationPort,
            clock = clock
        )
    }

    @Test
    fun `create should generate IDs and save payment with payment orders`() {
        // Given
        val paymentId = 123L
        val paymentOrderId1 = 456L
        val paymentOrderId2 = 789L

        whenever(idGeneratorPort.nextId(IdNamespaces.PAYMENT)).thenReturn(paymentId)
        whenever(idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER))
            .thenReturn(paymentOrderId1)
            .thenReturn(paymentOrderId2)

        whenever(serializationPort.toJson(any<Any>())).thenReturn("""{"eventType":"test"}""")

        val command = CreatePaymentCommand(
            orderId = OrderId("order-123"),
            buyerId = BuyerId("buyer-456"),
            totalAmount = Amount(200000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(100000L, "USD")),
                PaymentLine(SellerId("seller-2"), Amount(100000L, "USD"))
            )
        )

        // When
        val result = service.create(command)

        // Then
        assertNotNull(result)
        assertEquals(PaymentId(paymentId), result.paymentId)
        assertEquals(2, result.paymentOrders.size)
        assertEquals(PaymentOrderId(paymentOrderId1), result.paymentOrders[0].paymentOrderId)
        assertEquals(PaymentOrderId(paymentOrderId2), result.paymentOrders[1].paymentOrderId)

        // Verify interactions
        verify(paymentRepository).save(any())
        verify(paymentOrderRepository).insertAll(argThat { list ->
            list.size == 2 &&
                    list[0].paymentOrderId.value == paymentOrderId1 &&
                    list[1].paymentOrderId.value == paymentOrderId2
        })
        verify(outboxEventPort).saveAll(argThat { events -> events.size == 2 })
    }

    @Test
    fun `create should generate correct number of payment order IDs based on payment lines`() {
        // Given
        whenever(idGeneratorPort.nextId(IdNamespaces.PAYMENT)).thenReturn(100L)
        whenever(idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER)).thenReturn(200L, 201L, 202L)
        whenever(serializationPort.toJson(any<Any>())).thenReturn("""{"eventType":"test"}""")

        val command = CreatePaymentCommand(
            orderId = OrderId("order-1"),
            buyerId = BuyerId("buyer-1"),
            totalAmount = Amount(300000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(100000L, "USD")),
                PaymentLine(SellerId("seller-2"), Amount(100000L, "USD")),
                PaymentLine(SellerId("seller-3"), Amount(100000L, "USD"))
            )
        )

        // When
        val result = service.create(command)

        // Then
        assertEquals(3, result.paymentOrders.size)
        verify(idGeneratorPort, times(1)).nextId(IdNamespaces.PAYMENT)
        verify(idGeneratorPort, times(3)).nextId(IdNamespaces.PAYMENT_ORDER)
    }

    @Test
    fun `create should create outbox events for each payment order`() {
        // Given
        whenever(idGeneratorPort.nextId(IdNamespaces.PAYMENT)).thenReturn(100L)
        whenever(idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER)).thenReturn(200L, 201L)
        whenever(serializationPort.toJson(any<Any>())).thenReturn("""{"test":"data"}""")

        val command = CreatePaymentCommand(
            orderId = OrderId("order-1"),
            buyerId = BuyerId("buyer-1"),
            totalAmount = Amount(200000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(100000L, "USD")),
                PaymentLine(SellerId("seller-2"), Amount(100000L, "USD"))
            )
        )

        // When
        service.create(command)

        // Then
        verify(outboxEventPort).saveAll(argThat { events -> events.size == 2 })
    }

    @Test
    fun `create should use clock for timestamps`() {
        // Given
        val fixedInstant = Instant.parse("2024-06-15T10:30:00Z")
        val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

        val serviceWithFixedClock = CreatePaymentService(
            idGeneratorPort = idGeneratorPort,
            paymentRepository = paymentRepository,
            paymentOrderRepository = paymentOrderRepository,
            outboxEventPort = outboxEventPort,
            serializationPort = serializationPort,
            clock = fixedClock
        )

        whenever(idGeneratorPort.nextId(IdNamespaces.PAYMENT)).thenReturn(100L)
        whenever(idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER)).thenReturn(200L)
        whenever(serializationPort.toJson(any<Any>())).thenReturn("{}")

        val command = CreatePaymentCommand(
            orderId = OrderId("order-1"),
            buyerId = BuyerId("buyer-1"),
            totalAmount = Amount(100000L, "USD"),
            paymentLines = listOf(
                PaymentLine(SellerId("seller-1"), Amount(100000L, "USD"))
            )
        )

        // When
        val result = serviceWithFixedClock.create(command)

        // Then
        val expectedLocalDateTime = LocalDateTime.ofInstant(fixedInstant, fixedClock.zone)
        assertEquals(expectedLocalDateTime, result.createdAt)
        result.paymentOrders.forEach { order ->
            assertEquals(expectedLocalDateTime, order.createdAt)
        }
    }
}