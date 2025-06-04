package com.dogancaglar.paymentservice.application.helper

import com.dogancaglar.paymentservice.config.id.IdNamespaces
import com.dogancaglar.paymentservice.domain.internal.model.Payment
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.IdGeneratorPort
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
        val publicPaymentId = "payment-$paymentId"

        val paymentOrders = request.paymentOrders.map {
            val paymentOrderId = idGenerator.nextId(IdNamespaces.PAYMENT_ORDER)
            val publicPaymentOrderId = "paymentorder-$paymentOrderId"

            PaymentOrder.createNew(
                paymentOrderId = paymentOrderId,
                publicPaymentOrderId = publicPaymentOrderId,
                paymentId = paymentId,
                publicPaymentId = publicPaymentId,
                sellerId = it.sellerId,
                amount = AmountMapper.toDomain(it.amount),
                createdAt = now,
            )
        }

        return Payment.createNew(
            paymentId = paymentId,
            publicPaymentId = publicPaymentId,
            buyerId = request.buyerId,
            orderId = request.orderId,
            totalAmount = AmountMapper.toDomain(request.totalAmount),
            paymentOrders = paymentOrders,
            createdAt = now,
        )
    }
}