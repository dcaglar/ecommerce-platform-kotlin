package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.application.constants.IdNamespaces
import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.*
import com.dogancaglar.paymentservice.domain.util.PaymentFactory
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.util.PaymentOrderFactory
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        idGeneratorPort = mockk()
        paymentRepository = mockk()
        paymentOrderRepository = mockk()
        outboxEventPort = mockk()
        serializationPort = mockk()
        clock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneId.of("UTC"))

        // 🆕 add these
        val paymentOrderDomainEventMapper = PaymentOrderDomainEventMapper(clock)
        val paymentFactory = PaymentFactory(clock)

        service = CreatePaymentService(
            idGeneratorPort = idGeneratorPort,
            paymentRepository = paymentRepository,
            paymentOrderRepository = paymentOrderRepository,
            outboxEventPort = outboxEventPort,
            serializationPort = serializationPort,
            clock = clock,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper,
            paymentFactory = paymentFactory
        )
    }

    @Test
    fun `create should generate IDs and save payment with payment orders`() {
        // Given
        val paymentId = 123L
        val paymentOrderId1 = 456L
        val paymentOrderId2 = 789L

        every { idGeneratorPort.nextId(IdNamespaces.PAYMENT) } returns paymentId
        every { idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER) } returnsMany listOf(paymentOrderId1, paymentOrderId2)
        every { serializationPort.toJson<Any>(match { it is com.dogancaglar.common.event.EventEnvelope<*> }) } returns """{"eventType":"test"}"""
        every { paymentRepository.save(match { it is com.dogancaglar.paymentservice.domain.model.Payment }) } returns Unit
        every { paymentOrderRepository.insertAll(match { it is List<*> && it.size == 2 }) } returns Unit
        every { outboxEventPort.saveAll(match { it is List<*> && it.size == 2 }) } returns emptyList()

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
        verify(exactly = 1) { 
            paymentRepository.save(match { payment ->
                payment.paymentId == PaymentId(paymentId) &&
                payment.publicPaymentId == "payment-$paymentId" &&
                payment.buyerId == BuyerId("buyer-456") &&
                payment.orderId == OrderId("order-123") &&
                payment.totalAmount == Amount(200000L, "USD") &&
                payment.status == PaymentStatus.INITIATED &&
                payment.paymentOrders.size == 2
            })
        }
        verify(exactly = 1) {
            paymentOrderRepository.insertAll(match { list ->
                list.size == 2 &&
                        list[0].paymentOrderId.value == paymentOrderId1 &&
                        list[1].paymentOrderId.value == paymentOrderId2
            })
        }
        verify(exactly = 1) { outboxEventPort.saveAll(match { it.size == 2 }) }
    }

    @Test
    fun `create should generate correct number of payment order IDs based on payment lines`() {
        // Given
        every { idGeneratorPort.nextId(IdNamespaces.PAYMENT) } returns 100L
        every { idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER) } returnsMany listOf(200L, 201L, 202L)
        every { serializationPort.toJson<Any>(match { it is com.dogancaglar.common.event.EventEnvelope<*> }) } returns """{"eventType":"test"}"""
        every { paymentRepository.save(match { it is com.dogancaglar.paymentservice.domain.model.Payment }) } returns Unit
        every { paymentOrderRepository.insertAll(match { it is List<*> && it.size == 3 }) } returns Unit
        every { outboxEventPort.saveAll(match { it is List<*> && it.size == 3 }) } returns emptyList()

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
        verify(exactly = 1) { idGeneratorPort.nextId(IdNamespaces.PAYMENT) }
        verify(exactly = 3) { idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER) }
    }

    @Test
    fun `create should create outbox events for each payment order`() {
        // Given
        every { idGeneratorPort.nextId(IdNamespaces.PAYMENT) } returns 100L
        every { idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER) } returnsMany listOf(200L, 201L)
        every { serializationPort.toJson<Any>(match { it is com.dogancaglar.common.event.EventEnvelope<*> }) } returns """{"test":"data"}"""
        every { paymentRepository.save(match { it is com.dogancaglar.paymentservice.domain.model.Payment }) } returns Unit
        every { paymentOrderRepository.insertAll(match { it is List<*> && it.size == 2 }) } returns Unit
        every { outboxEventPort.saveAll(match { it is List<*> && it.size == 2 }) } returns emptyList()

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
        verify(exactly = 1) { outboxEventPort.saveAll(match { it.size == 2 }) }
    }
    @Test
    fun `create should use clock for timestamps`() {
        // Given
        val fixedInstant = Instant.parse("2024-06-15T10:30:00Z")
        val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

        // NEW deps that the service now requires
        val paymentOrderFactory = PaymentOrderFactory()
        val paymentOrderDomainEventMapper = PaymentOrderDomainEventMapper(fixedClock)
        val paymentFactory = PaymentFactory(fixedClock)

        val serviceWithFixedClock = CreatePaymentService(
            idGeneratorPort = idGeneratorPort,
            paymentRepository = paymentRepository,
            paymentOrderRepository = paymentOrderRepository,
            outboxEventPort = outboxEventPort,
            serializationPort = serializationPort,
            clock = fixedClock,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper,
            paymentFactory = paymentFactory
        )

        every { idGeneratorPort.nextId(IdNamespaces.PAYMENT) } returns 100L
        every { idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER) } returns 200L
        every { serializationPort.toJson<Any>(match { it is com.dogancaglar.common.event.EventEnvelope<*> }) } returns "{}"
        every { paymentRepository.save(match { it is com.dogancaglar.paymentservice.domain.model.Payment }) } returns Unit
        every { paymentOrderRepository.insertAll(match { it is List<*> && it.size == 1 }) } returns Unit
        every { outboxEventPort.saveAll(match { it is List<*> && it.size == 1 }) } returns emptyList()

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
