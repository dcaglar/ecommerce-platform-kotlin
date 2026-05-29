package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
import java.time.LocalDateTime

/**
 * Maps between domain PaymentOrder aggregates and their event representations.
 * Used for outbox serialization and consumer-side reconstruction.
 */
object PaymentOrderDomainEventEntityMapper {





    /** 🔹 Domain → Event: when PaymentOrder is first created */
    fun toPaymentOrderCaptureReceived(order: PaymentOrder): PaymentOrderCaptureReceived {
        val now = Utc.nowInstant()
        return PaymentOrderCaptureReceived.from(
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
    fun toPaymentOrderFinalized(order: PaymentOrder, now: LocalDateTime, status: PaymentOrderStatus): PaymentOrderFinalized =
        PaymentOrderFinalized.from(
           order = order,
            now = Utc.toInstant(now),
            status=status
        )



}