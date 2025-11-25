package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentAuthorizedLine
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.dogancaglar.paymentservice.domain.model.*
import com.dogancaglar.paymentservice.domain.model.vo.*
import java.time.Clock
import java.time.LocalDateTime

/**
 * Maps between domain PaymentOrder aggregates and their event representations.
 * Used for outbox serialization and consumer-side reconstruction.
 */
class PaymentOrderDomainEventMapper {

    fun toPaymentAuthorized(updated: Payment,paymentLines: List<PaymentLine>): PaymentAuthorized{
        return PaymentAuthorized.from(
            payment = updated,
            paymentLines = paymentLines.map {
                PaymentAuthorizedLine.of(
                    sellerId = it.sellerId.value,
                    amountValue = it.amount.quantity,
                    currency = it.amount.currency.currencyCode
                )
            },
            authorizedAt = Utc.toInstant(updated.createdAt)
        )
    }




    /** 🔹 Domain → Event: when PaymentOrder is first created */
    fun toPaymentOrderCreated(order: PaymentOrder): PaymentOrderCreated {
        val now = Utc.nowInstant()
        return PaymentOrderCreated.from(
            order = order,
            now = now
        )
    }

    /** 🔹 Domain → Event: PSP call requested */
    fun toPaymentOrderCaptureCommand(order: PaymentOrder, attempt: Int): PaymentOrderCaptureCommand {
        val now = Utc.nowInstant()
        return PaymentOrderCaptureCommand.from(
            order = order,
            attempt = attempt,
            now = now
        )
    }

    /** 🔹 Domain → Event: successful final state */
    fun toPaymentOrderFinalized(order: PaymentOrder,now: LocalDateTime,status: PaymentOrderStatus): PaymentOrderFinalized =
        PaymentOrderFinalized.from(
           order = order,
            now = Utc.toInstant(now),
            status=status
        )



}