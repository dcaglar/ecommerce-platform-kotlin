package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.domain.event.*
import com.dogancaglar.paymentservice.domain.model.*
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.vo.*
import java.time.Clock
import java.time.LocalDateTime

/**
 * Maps between domain PaymentOrder aggregates and their event representations.
 * Used for outbox serialization and consumer-side reconstruction.
 */
class PaymentOrderDomainEventMapper(
    private val clock: Clock = Clock.systemUTC() // inject clock for deterministic timestamps in tests
) {

    /** 🔹 Domain → Event: when PaymentOrder is first created */
    fun toPaymentOrderCreated(order: PaymentOrder): PaymentOrderCreated =
        PaymentOrderCreated(
            paymentOrderId = order.paymentOrderId.value.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.value.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId.value,
            retryCount = order.retryCount,
            createdAt = LocalDateTime.now(clock),
            status = order.status.name,
            amountValue = order.amount.quantity,
            currency = order.amount.currency.currencyCode
        )

    /** 🔹 Domain → Event: PSP call requested */
    fun toPaymentOrderPspCallRequested(order: PaymentOrder, attempt: Int): PaymentOrderPspCallRequested =
        PaymentOrderPspCallRequested(
            paymentOrderId = order.paymentOrderId.value.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.value.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId.value,
            retryCount = attempt,
            retryReason = order.retryReason,
            lastErrorMessage = order.lastErrorMessage,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
            status = order.status.name,
            amountValue = order.amount.quantity,
            currency = order.amount.currency.currencyCode
        )

    /** 🔹 Domain → Event: successful final state */
    fun toPaymentOrderSucceeded(order: PaymentOrder): PaymentOrderSucceeded =
        PaymentOrderSucceeded(
            paymentOrderId = order.paymentOrderId.value.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.value.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId.value,
            amountValue = order.amount.quantity,
            currency = order.amount.currency.currencyCode,
            status = order.status.name,
            )

    fun toPaymentOrderFailed(order: PaymentOrder): PaymentOrderFailed =
        PaymentOrderFailed(
            paymentOrderId = order.paymentOrderId.value.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.value.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId.value,
            amountValue = order.amount.quantity,
            currency = order.amount.currency.currencyCode,
            status = order.status.name,
            )

    /** 🔹 Domain → Event: schedule PSP status check */
    fun toPaymentOrderStatusCheckRequested(order: PaymentOrder): PaymentOrderStatusCheckRequested =
        PaymentOrderStatusCheckRequested(
            paymentOrderId = order.paymentOrderId.value.toString(),
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.value.toString(),
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId.value,
            retryCount = order.retryCount,
            retryReason = order.retryReason,
            createdAt = LocalDateTime.now(clock),
            updatedAt = LocalDateTime.now(clock),
            status = order.status.name,
            amountValue = order.amount.quantity,
            currency = order.amount.currency.currencyCode
        )

    /** 🔹 Event → Domain aggregate (consumer-side reconstruction) */
    fun fromEvent(event: PaymentOrderEvent): PaymentOrder =
        PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(event.paymentOrderId.toLong()))
            .publicPaymentOrderId(event.publicPaymentOrderId)
            .paymentId(PaymentId(event.paymentId.toLong()))
            .publicPaymentId(event.publicPaymentId)
            .sellerId(SellerId(event.sellerId))
            .amount(Amount.of(event.amountValue, Currency(event.currency)))
            .status(PaymentOrderStatus.valueOf(event.status))
            .createdAt(event.createdAt)
            .updatedAt(event.updatedAt)
            .retryCount(event.retryCount)
            .retryReason(event.retryReason)
            .lastErrorMessage(event.lastErrorMessage)
            .buildFromPersistence()

    /** 🔹 Copy helper: change event status immutably */
    fun copyWithStatus(event: PaymentOrderCreated, newStatus: String): PaymentOrderCreated =
        event.copy(status = newStatus)
}