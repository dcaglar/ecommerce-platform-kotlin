package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentAuthorizedLine
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.events.PaymentOrderFailed
import com.dogancaglar.paymentservice.application.events.PaymentOrderSucceeded
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

    fun toPaymentAuthorized(updated: Payment,paymentLines: List<PaymentLine>): PaymentAuthorized{
        return PaymentAuthorized.create(
            paymentId = updated.paymentId.value.toString(),
            buyerId = updated.buyerId.value,
            orderId = updated.orderId.value,
            totalAmountValue = updated.totalAmount.quantity,
            currency = updated.totalAmount.currency.currencyCode,
            paymentLines = paymentLines.map {
                PaymentAuthorizedLine.of(
                    sellerId = it.sellerId.value,
                    amountValue = it.amount.quantity,
                    currency = it.amount.currency.currencyCode
                )
            },
            status = updated.status.name
        )
    }




    /** 🔹 Domain → Event: when PaymentOrder is first created */
    fun toPaymentOrderCreated(order: PaymentOrder): PaymentOrderCreated =
        PaymentOrderCreated.create(
            paymentOrderId = order.paymentOrderId.value.toString(),
            paymentId = order.paymentId.value.toString(),
            sellerId = order.sellerId.value,
            retryCount = order.retryCount,
            createdAt = LocalDateTime.now(clock),
            status = order.status.name,
            amountValue = order.amount.quantity,
            currency = order.amount.currency.currencyCode
        )

    /** 🔹 Domain → Event: PSP call requested */
    fun toPaymentOrderCaptureCommand(order: PaymentOrder, attempt: Int): PaymentOrderCaptureCommand =
        PaymentOrderCaptureCommand.create(
            paymentOrderId = order.paymentOrderId.value.toString(),
            publicPaymentOrderId = order.paymentOrderId.toPublicPaymentOrderId(),
            paymentId = order.paymentId.value.toString(),
            publicPaymentId = order.paymentId.toPublicPaymentId(),
            sellerId = order.sellerId.value,
            retryCount = attempt,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
            status = order.status.name,
            amountValue = order.amount.quantity,
            currency = order.amount.currency.currencyCode
        )

    /** 🔹 Domain → Event: successful final state */
    fun toPaymentOrderSucceeded(order: PaymentOrder): PaymentOrderSucceeded =
        PaymentOrderSucceeded.create(
            paymentOrderId = order.paymentOrderId.value.toString(),
            paymentId = order.paymentId.value.toString(),
            sellerId = order.sellerId.value,
            amountValue = order.amount.quantity,
            currency = order.amount.currency.currencyCode,
            status = order.status.name
        )

    fun toPaymentOrderFailed(order: PaymentOrder): PaymentOrderFailed =
        PaymentOrderFailed.create(
            paymentOrderId = order.paymentOrderId.value.toString(),
            paymentId = order.paymentId.value.toString(),
            sellerId = order.sellerId.value,
            amountValue = order.amount.quantity,
            currency = order.amount.currency.currencyCode,
            status = order.status.name
        )

    /** 🔹 Event → Domain aggregate (consumer-side reconstruction) */
    fun fromEvent(event: PaymentOrderEvent): PaymentOrder =
        PaymentOrder.rehydrate(
            paymentOrderId = PaymentOrderId(event.paymentOrderId.toLong()),
            paymentId = PaymentId(event.paymentId.toLong()),
            sellerId = SellerId(event.sellerId),
            amount = Amount.of(event.amountValue, Currency(event.currency)),
            status = PaymentOrderStatus.valueOf(event.status),
            retryCount = event.retryCount,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt
        )

    fun fromAuthorizedEvent(event: PaymentOrderEvent): PaymentOrder =
        PaymentOrder.rehydrate(
            paymentOrderId = PaymentOrderId(event.paymentOrderId.toLong()),
            paymentId = PaymentId(event.paymentId.toLong()),
            sellerId = SellerId(event.sellerId),
            amount = Amount.of(event.amountValue, Currency(event.currency)),
            status = PaymentOrderStatus.valueOf(event.status),
            retryCount = event.retryCount,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt
        )

    /** 🔹 Copy helper: change event status immutably */
    fun copyWithStatus(event: PaymentOrderCreated, newStatus: String): PaymentOrderCreated =
        event.copy(status = newStatus)
}