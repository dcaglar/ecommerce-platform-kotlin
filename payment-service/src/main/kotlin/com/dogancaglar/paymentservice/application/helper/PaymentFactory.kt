package com.dogancaglar.paymentservice.application.helper

import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.port.IdGeneratorPort
import com.dogancaglar.paymentservice.domain.port.IdNamespaces
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.mapper.AmountMapper
import java.time.Clock
import java.time.LocalDateTime

class PaymentFactory(
    private val idGenerator: IdGeneratorPort,
    private val clock: Clock
) {
    fun createFrom(request: PaymentRequestDTO): Payment {
        val now = LocalDateTime.now(clock)

        val paymentId = idGenerator.nextId(IdNamespaces.PAYMENT)
        val paymentPublicId = "payment-$paymentId"

        val paymentOrderIdPairs = request.paymentOrders.map {
            val orderId = idGenerator.nextId(IdNamespaces.PAYMENT_ORDER)
            orderId to "paymentorder-$orderId"
        }

        val paymentOrders = request.paymentOrders.mapIndexed { index, dto ->
            val (orderId, orderPublicId) = paymentOrderIdPairs[index]
            PaymentOrder(
                paymentOrderId = orderId,
                publicPaymentOrderId = orderPublicId,
                paymentId = paymentId,
                publicPaymentId = paymentPublicId,
                sellerId = dto.sellerId,
                amount = AmountMapper.toDomain(dto.amount),
                status = PaymentOrderStatus.INITIATED,
                createdAt = now,
                updatedAt = now
            )
        }

        return Payment(
            paymentId = paymentId,
            paymentPublicId = paymentPublicId,
            buyerId = request.buyerId,
            orderId = request.orderId,
            totalAmount = AmountMapper.toDomain(request.totalAmount),
            status = PaymentStatus.INITIATED,
            createdAt = now,
            paymentOrders = paymentOrders
        )
    }


}