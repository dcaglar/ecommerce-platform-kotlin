package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentPipelineAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentAuthorizedLine
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
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

    fun toPaymentAuthorized(updated: Payment,paymentLines: List<PaymentLine>): PaymentPipelineAuthorized{
        return PaymentPipelineAuthorized.create(
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
            status = updated.status.name,
            authorizedAt = LocalDateTime.now(clock)
        )
    }




    /** 🔹 Domain → Event: when PaymentOrder is first created */
    fun toPaymentOrderCreated(order: PaymentOrder): PaymentOrderCreated =
        PaymentOrderCreated.from(
            order = order,
            now = LocalDateTime.now(clock)
        )

    /** 🔹 Domain → Event: PSP call requested */
    fun toPaymentOrderCaptureCommand(order: PaymentOrder, attempt: Int): PaymentOrderCaptureCommand =
        PaymentOrderCaptureCommand.from(
            order = order,
            attempt = attempt,
            now = LocalDateTime.now(clock)
        )

    /** 🔹 Domain → Event: successful final state */
    fun toPaymentOrderFinalized(order: PaymentOrder,now: LocalDateTime,status:String): PaymentOrderFinalized =
        PaymentOrderFinalized.from(
           order = order,
            now = now,
            status=status
        )

    fun snapshotFrom(event: PaymentOrderEvent): PaymentOrderSnapshot =
        PaymentOrderSnapshot(
            paymentOrderId = event.paymentOrderId,
            paymentId = event.paymentId,
            sellerId = event.sellerId,
            amountValue = event.amountValue,
            currency = event.currency,
            timestamp = event.timestamp
        )

    fun fromAuthorizedEvent(event: PaymentOrderEvent): PaymentOrderSnapshot =
        snapshotFrom(event)


}