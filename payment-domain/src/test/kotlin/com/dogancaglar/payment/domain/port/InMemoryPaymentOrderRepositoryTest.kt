package com.dogancaglar.payment.domain.port

import com.dogancaglar.payment.domain.factory.PaymentFactory
import com.dogancaglar.payment.domain.model.Amount
import com.dogancaglar.payment.domain.model.Payment
import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.PaymentOrderStatus
import com.dogancaglar.payment.domain.model.command.CreatePaymentCommand
import com.dogancaglar.payment.domain.model.vo.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime

class InMemoryPaymentOrderRepositoryTest {
    private lateinit var repository: InMemoryPaymentOrderRepository
    private val now = LocalDateTime.now()
    private val sellerId = SellerId(1L.toString())
    private val paymentFactory = PaymentFactory(Clock.systemUTC())

    private fun createPaymentWithOrders(
        paymentId: PaymentId,
        orderIds: List<PaymentOrderId>,
        statusList: List<PaymentOrderStatus> = orderIds.map { PaymentOrderStatus.PENDING }
    ): Payment {
        val orderId = OrderId("order-1")
        val buyerId = BuyerId("buyer-1")
        val totalAmount = Amount(BigDecimal.valueOf(1000), "USD")
        val paymentLines = orderIds.map { PaymentLine(sellerId, Amount(BigDecimal.valueOf(1000), "USD")) }
        val cmd = CreatePaymentCommand(
            orderId = orderId,
            buyerId = buyerId,
            totalAmount = totalAmount,
            paymentLines = paymentLines
        )
        val payment = paymentFactory.createPayment(
            cmd,
            paymentId = paymentId,
            paymentOrderIdList = orderIds
        )
        return orderIds.zip(statusList).fold(payment) { acc, (orderId, status) ->
            var order = PaymentOrder.createNew(
                paymentOrderId = orderId,
                publicPaymentOrderId = "paymentorder-${orderId.value}",
                paymentId = paymentId,
                publicPaymentId = "payment-$paymentId",
                sellerId = sellerId,
                amount = com.dogancaglar.payment.domain.model.Amount(BigDecimal.valueOf(1000), "USD"),
                createdAt = now
            )
            order = when (status) {
                PaymentOrderStatus.PENDING -> order.markAsPending()
                PaymentOrderStatus.SUCCESSFUL -> order.markAsPaid()
                PaymentOrderStatus.FAILED -> order.markAsFailed()
                PaymentOrderStatus.FINALIZED_FAILED -> order.markAsFinalizedFailed()
                PaymentOrderStatus.INITIATED -> order
                PaymentOrderStatus.DECLINED, PaymentOrderStatus.UNKNOWN, PaymentOrderStatus.TIMEOUT, PaymentOrderStatus.PSP_UNAVAILABLE, PaymentOrderStatus.AUTH_NEEDED, PaymentOrderStatus.CAPTURE_PENDING -> order // Extend as needed
            }
            acc.addPaymentOrder(order)
        }
    }

    @BeforeEach
    fun setUp() {
        repository = InMemoryPaymentOrderRepository()
    }

    @Test
    fun `save and count by paymentId`() {
        val payment = createPaymentWithOrders(PaymentId(10), listOf(PaymentOrderId(1L)))
        payment.paymentOrders.forEach { repository.save(it) }
        assertEquals(1, repository.countByPaymentId(PaymentId(10)))
    }

    @Test
    fun `saveAll and verify all persisted`() {
        val payment = createPaymentWithOrders(PaymentId(10), listOf(PaymentOrderId(1L), PaymentOrderId(2L)))
        repository.saveAll(payment.paymentOrders)
        assertEquals(2, repository.countByPaymentId(PaymentId(10)))
    }

    @Test
    fun `count by status and paymentId`() {
        val payment = createPaymentWithOrders(
            PaymentId(10),
            listOf(PaymentOrderId(1L), PaymentOrderId(2L)),
            listOf(PaymentOrderStatus.PENDING, PaymentOrderStatus.SUCCESSFUL)
        )
        repository.saveAll(payment.paymentOrders)
        assertEquals(1, repository.countByPaymentIdAndStatusIn(PaymentId(10), listOf("PENDING")))
        assertEquals(1, repository.countByPaymentIdAndStatusIn(PaymentId(10), listOf("SUCCESSFUL")))
        assertEquals(2, repository.countByPaymentIdAndStatusIn(PaymentId(10), listOf("PENDING", "SUCCESSFUL")))
    }

    @Test
    fun `exists by paymentId and status`() {
        val payment = createPaymentWithOrders(
            PaymentId(10),
            listOf(PaymentOrderId(1L), PaymentOrderId(2L)),
            listOf(PaymentOrderStatus.PENDING)
        )
        repository.saveAll(payment.paymentOrders)
        assertTrue(repository.existsByPaymentIdAndStatus(PaymentId(10), "PENDING"))
        assertFalse(repository.existsByPaymentIdAndStatus(PaymentId(10), "SUCCESSFUL"))
    }

    @Test
    fun `getMaxPaymentOrderId returns correct max or zero`() {
        assertEquals(PaymentOrderId(0L), repository.getMaxPaymentOrderId())
        val payment = createPaymentWithOrders(
            PaymentId(10L),
            listOf(PaymentOrderId(5L), PaymentOrderId(2L), PaymentOrderId(7L)),
            listOf(PaymentOrderStatus.PENDING, PaymentOrderStatus.SUCCESSFUL, PaymentOrderStatus.FINALIZED_FAILED)
        )
        repository.saveAll(payment.paymentOrders)
        assertEquals(PaymentOrderId(7L), repository.getMaxPaymentOrderId())
    }
}
